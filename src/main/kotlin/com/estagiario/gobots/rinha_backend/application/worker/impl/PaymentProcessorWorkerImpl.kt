package com.estagiario.gobots.rinha_backend.application.worker.impl

import com.estagiario.gobots.rinha_backend.application.ports.PaymentProcessorClient
import com.estagiario.gobots.rinha_backend.application.worker.OutboxRelay
import com.estagiario.gobots.rinha_backend.domain.HealthCheckState
import com.estagiario.gobots.rinha_backend.domain.PaymentEvent
import com.estagiario.gobots.rinha_backend.domain.PaymentEventStatus
import com.estagiario.gobots.rinha_backend.domain.PaymentStatus
import com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository.PaymentEventRepository
import com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository.PaymentRepository
import com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository.ProcessorHealthRepository
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.*

private val logger = KotlinLogging.logger {}

@Component
class PaymentProcessorWorkerImpl(
    private val outbox: OutboxRelay,
    private val paymentRepo: PaymentRepository,
    private val eventRepo: PaymentEventRepository,
    private val processorHealthRepo: ProcessorHealthRepository,
    @Qualifier("defaultClient") private val defaultClient: PaymentProcessorClient,
    @Qualifier("fallbackClient") private val fallbackClient: PaymentProcessorClient
) {

    private val batchSize = 15
    private val maxConcurrency = 3
    private val reqTimeout = Duration.ofSeconds(4)

    /**
     * Tick: claim batch + process with controlled concurrency.
     * Caller should call this periodically (e.g. from scheduler) passing a worker id.
     */
    fun tick(owner: String): Mono<Void> {
        return outbox.claimPendingBatch(owner, batchSize)
            .flatMap({ ev -> processEvent(ev) }, maxConcurrency)
            .onErrorContinue { t, _ -> logger.warn(t) { "Falha ao processar tick: ${t.message}" } }
            .then()
    }

    private fun processEvent(ev: PaymentEvent): Mono<Void> {
        val correlationId = UUID.fromString(ev.correlationId)
        return paymentRepo.findByCorrelationId(ev.correlationId)
            .switchIfEmpty(Mono.defer {
                logger.warn { "Payment não encontrado p/ correlationId=${ev.correlationId}" }
                Mono.empty()
            })
            .flatMap { payment ->
                val amount = BigDecimal(payment.amount).movePointLeft(2)
                val requestedAt = payment.requestedAt

                tryDefaultThenFallback(correlationId, amount, requestedAt)
                    .flatMap { usedProcessor ->
                        val updated = payment.copy(
                            status = PaymentStatus.SUCCESS, // 🔄 SUCESSO -> SUCCESS
                            lastUpdatedAt = Instant.now(),
                            processorUsed = usedProcessor
                        )
                        paymentRepo.save(updated).then(markProcessed(ev))
                    }
                    .onErrorResume { err ->
                        logger.warn(err) { "Process failed for ${ev.correlationId}: ${err.message}" }
                        val failed = payment.copy(
                            status = PaymentStatus.FAILURE, // 🔄 FALHA -> FAILURE
                            lastUpdatedAt = Instant.now(),
                            lastErrorMessage = err.message
                        )
                        paymentRepo.save(failed).then(markProcessed(ev))
                    }
            }
    }

    private fun tryDefaultThenFallback(
        correlationId: UUID,
        amount: BigDecimal,
        requestedAt: Instant
    ): Mono<String> {

        val defaultOkMono = processorHealthRepo.findById("default")
            .map { it.state == HealthCheckState.HEALTHY }
            .defaultIfEmpty(true) // if absent, assume healthy (first-start behavior)

        val fallbackOkMono = processorHealthRepo.findById("fallback")
            .map { it.state == HealthCheckState.HEALTHY }
            .defaultIfEmpty(true)

        return Mono.zip(defaultOkMono, fallbackOkMono).flatMap { pair ->
            val defaultOk = pair.t1
            val fallbackOk = pair.t2

            fun attemptDefault(): Mono<String> =
                defaultClient.process(correlationId, amount, requestedAt)
                    .timeout(reqTimeout)
                    .thenReturn("default")

            fun attemptFallback(): Mono<String> =
                fallbackClient.process(correlationId, amount, requestedAt)
                    .timeout(reqTimeout)
                    .thenReturn("fallback")

            when {
                defaultOk -> attemptDefault().onErrorResume { if (fallbackOk) attemptFallback() else Mono.error(it) }
                fallbackOk -> attemptFallback().onErrorResume { if (defaultOk) attemptDefault() else Mono.error(it) }
                else -> {
                    // Neither declared healthy – try default then fallback as last resort.
                    attemptDefault().onErrorResume { attemptFallback() }
                }
            }
        }
    }

    private fun markProcessed(ev: PaymentEvent): Mono<Void> {
        val done = ev.copy(
            status = PaymentEventStatus.PROCESSED,
            processedAt = Instant.now(),
            owner = null
        )
        return eventRepo.save(done).then()
    }
}