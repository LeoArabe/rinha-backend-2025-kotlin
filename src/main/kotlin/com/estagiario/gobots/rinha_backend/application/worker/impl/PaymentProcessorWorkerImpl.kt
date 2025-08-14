// application/worker/impl/PaymentProcessorWorkerImpl.kt
package com.estagiario.gobots.rinha_backend.application.worker.impl

import com.estagiario.gobots.rinha_backend.application.ports.PaymentProcessorClient
import com.estagiario.gobots.rinha_backend.application.worker.OutboxRelayOptimized
import com.estagiario.gobots.rinha_backend.domain.Payment
import com.estagiario.gobots.rinha_backend.domain.PaymentEvent
import com.estagiario.gobots.rinha_backend.domain.PaymentEventStatus
import com.estagiario.gobots.rinha_backend.domain.PaymentStatus
import com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository.PaymentEventRepository
import com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository.PaymentRepository
import com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository.ProcessorHealthRepository
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.switchIfEmpty
import java.time.Duration
import java.time.Instant
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Worker reativo que:
 * - faz claim atômico da outbox
 * - decide default/fallback (com base no health)
 * - atualiza Payment com WriteConcern MAJORITY
 * - marca o evento como PROCESSED
 */
@Component
class PaymentProcessorWorkerImpl(
    private val relay: OutboxRelayOptimized,
    private val paymentRepo: PaymentRepository,
    private val eventRepo: PaymentEventRepository,
    private val healthRepo: ProcessorHealthRepository,
    private val mongo: ReactiveMongoTemplate,
    @Qualifier("defaultClient") private val defaultClient: PaymentProcessorClient,
    @Qualifier("fallbackClient") private val fallbackClient: PaymentProcessorClient,
    @Value("\${app.worker.batch-size:50}") private val batchSize: Int,
    @Value("\${app.worker.retry-seconds:5}") private val retrySeconds: Long
) {

    fun tick(owner: String): Mono<Void> =
        relay.claimPendingBatch(owner, batchSize)
            .flatMap { event -> processOne(owner, event) }
            .then()

    private fun processOne(owner: String, event: PaymentEvent): Mono<Void> {
        return paymentRepo.findByCorrelationId(event.correlationId)
            .switchIfEmpty {
                logger.warn { "Pagamento não encontrado para correlationId=${event.correlationId}" }
                markEventProcessed(event.id!!, false, "Pagamento não encontrado")
            }
            .flatMap { payment -> dispatchToProcessor(payment) }
            .flatMap { result -> applyResult(event, result) }
            .onErrorResume { t ->
                logger.warn(t) { "Falha no processamento do evento ${event.id}" }
                scheduleRetry(event.id!!, t.message)
            }
    }

    private data class DispatchResult(
        val payment: Payment,
        val success: Boolean,
        val processor: String?,
        val error: String?
    )

    private fun dispatchToProcessor(payment: Payment): Mono<DispatchResult> {
        // Escolha de processador baseada no último health conhecido (TTL curto)
        val chooseMono = healthRepo.findById("default")
            .map { it.state.name == "HEALTHY" }
            .defaultIfEmpty(true)

        return chooseMono.flatMap { defaultHealthy ->
            val client = if (defaultHealthy) defaultClient else fallbackClient
            val procName = if (defaultHealthy) "default" else "fallback"

            client.process(
                UUID.fromString(payment.correlationId),
                payment.toBigDecimal(),        // assume Payment#toBigDecimal no domínio
                payment.requestedAt
            )
                .timeout(Duration.ofSeconds(4))
                .thenReturn(DispatchResult(payment, true, procName, null))
                .onErrorResume { e ->
                    Mono.just(DispatchResult(payment, false, procName, e.message ?: "erro desconhecido"))
                }
        }
    }

    private fun applyResult(event: PaymentEvent, r: DispatchResult): Mono<Void> {
        val now = Instant.now()

        val paymentQ = Query.query(Criteria.where("correlationId").`is`(r.payment.correlationId))
        val paymentU = if (r.success) {
            Update()
                .set("status", PaymentStatus.SUCESSO.name)
                .set("processorUsed", r.processor)
                .set("lastUpdatedAt", now)
                .set("lastErrorMessage", null)
        } else {
            Update()
                .set("status", PaymentStatus.FALHA.name)
                .inc("attemptCount", 1)
                .set("lastUpdatedAt", now)
                .set("lastErrorMessage", r.error)
                .set("nextRetryAt", now.plusSeconds(retrySeconds))
        }

        // MAJORITY para estado auditável
        val updatePayment = mongo
            .withWriteConcern(com.mongodb.WriteConcern.MAJORITY)
            .updateFirst(paymentQ, paymentU, "payments")
            .then()

        val finalizeEvent = markEventProcessed(event.id!!, r.success, r.error)

        return updatePayment.then(finalizeEvent)
    }

    private fun markEventProcessed(eventId: String, success: Boolean, error: String?): Mono<Void> {
        val q = Query.query(Criteria.where("_id").`is`(eventId))
        val u = Update()
            .set("status", PaymentEventStatus.PROCESSADO.name)
            .set("processedAt", Instant.now())
            .set("error", error)
            .set("success", success)

        // W1 é suficiente para evento (não-auditável)
        return mongo
            .withWriteConcern(com.mongodb.WriteConcern.W1)
            .updateFirst(q, u, "payment_events")
            .then()
    }

    private fun scheduleRetry(eventId: String, error: String?): Mono<Void> {
        val q = Query.query(Criteria.where("_id").`is`(eventId))
        val u = Update()
            .set("status", PaymentEventStatus.PENDENTE.name)
            .set("owner", null)
            .set("processingAt", null)
            .set("nextRetryAt", Instant.now().plusSeconds(retrySeconds))
            .set("error", error)

        return mongo
            .withWriteConcern(com.mongodb.WriteConcern.W1)
            .updateFirst(q, u, "payment_events")
            .then()
    }
}
