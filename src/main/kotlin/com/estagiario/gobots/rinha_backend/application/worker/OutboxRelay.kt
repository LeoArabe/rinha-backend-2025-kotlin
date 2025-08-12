package com.estagiario.gobots.rinha_backend.application.worker

import com.estagiario.gobots.rinha_backend.domain.Payment
import com.estagiario.gobots.rinha_backend.domain.PaymentEvent
import com.estagiario.gobots.rinha_backend.domain.PaymentEventStatus
import com.estagiario.gobots.rinha_backend.application.worker.PaymentProcessorWorker
import com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository.PaymentEventRepository
import com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository.PaymentRepository
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.time.temporal.ChronoUnit

private val logger = KotlinLogging.logger {}

@Component
class OutboxRelay(
    private val mongoTemplate: ReactiveMongoTemplate,
    private val paymentEventRepository: PaymentEventRepository,
    private val paymentRepository: PaymentRepository,
    private val paymentProcessorWorker: PaymentProcessorWorker,
    @Value("\${app.instance-id:API-LOCAL}") private val instanceId: String,
    @Value("\${app.run-mode:api}") private val runMode: String,
    @Value("\${app.outbox.batch-size:10}") private val batchSize: Int,
    @Value("\${app.outbox.max-concurrency:3}") private val maxConcurrency: Int,
    @Value("\${app.outbox.lock-timeout-minutes:5}") private val lockTimeoutMinutes: Long,
    @Value("\${app.outbox.processing-delay-ms:500}") private val processingDelayMs: Long
) {

    @Scheduled(fixedDelayString = "\${app.outbox.processing-delay-ms:500}")
    fun scheduleOutboxProcessing() {
        if (runMode != "worker" && runMode != "hybrid") return

        claimAndProcessBatch()
            .doOnError { e -> logger.error(e) { "Error during outbox processing batch" } }
            .onErrorResume { Mono.empty() }
            .subscribe()
    }

    /**
     * Claim em lote + processamento concorrente limitado.
     * Busca candidatos (limit = batchSize), tenta marcar em lote via updateMulti,
     * re-busca apenas os eventos que esta instância claimou e processa com flatMap(maxConcurrency).
     */
    private fun claimAndProcessBatch(): Mono<Void> {
        val now = Instant.now()
        val lockExpiredThreshold = now.minus(lockTimeoutMinutes, ChronoUnit.MINUTES)

        val listQuery = Query()
            .addCriteria(
                Criteria.where("status").`is`(PaymentEventStatus.PENDING)
                    .andOperator(
                        Criteria().orOperator(
                            Criteria.where("owner").`is`(null),
                            Criteria.where("processingAt").lt(lockExpiredThreshold)
                        ),
                        Criteria().orOperator(
                            Criteria.where("nextRetryAt").exists(false),
                            Criteria.where("nextRetryAt").`is`(null),
                            Criteria.where("nextRetryAt").lte(now)
                        )
                    )
            )
            .with(Sort.by(Sort.Direction.ASC, "createdAt"))
            .limit(batchSize)

        return mongoTemplate.find(listQuery, PaymentEvent::class.java)
            .collectList()
            .flatMap { candidates ->
                if (candidates.isEmpty()) return@flatMap Mono.empty<Void>()

                val ids = candidates.mapNotNull { it.id }
                if (ids.isEmpty()) return@flatMap Mono.empty<Void>()

                val claimQuery = Query.query(Criteria.where("_id").`in`(ids).and("status").`is`(PaymentEventStatus.PENDING))
                val claimUpdate = Update()
                    .set("status", PaymentEventStatus.PROCESSING)
                    .set("owner", instanceId)
                    .set("processingAt", now)

                mongoTemplate.updateMulti(claimQuery, claimUpdate, PaymentEvent::class.java)
                    .flatMap { updateResult ->
                        if (updateResult.modifiedCount == 0L) {
                            return@flatMap Mono.empty<Void>()
                        }

                        val reFetch = Query.query(
                            Criteria.where("_id").`in`(ids)
                                .and("owner").`is`(instanceId)
                                .and("status").`is`(PaymentEventStatus.PROCESSING)
                        )

                        mongoTemplate.find(reFetch, PaymentEvent::class.java).collectList()
                            .flatMap { claimedEvents ->
                                if (claimedEvents.isEmpty()) return@flatMap Mono.empty<Void>()

                                val correlationIds = claimedEvents.map { it.correlationId }.distinct()

                                paymentRepository.findAllByCorrelationIdIn(correlationIds)
                                    .collectMap({ it.correlationId }, { it })
                                    .flatMap { paymentsMap ->
                                        Flux.fromIterable(claimedEvents)
                                            .flatMap({ event ->
                                                val payment = paymentsMap[event.correlationId]
                                                if (payment == null) {
                                                    // marca event processed para evitar retry infinito
                                                    paymentEventRepository.save(
                                                        event.copy(
                                                            status = PaymentEventStatus.PROCESSED,
                                                            processedAt = Instant.now()
                                                        )
                                                    ).then()
                                                } else {
                                                    paymentProcessorWorker.processPaymentFromQueue(event, payment)
                                                        .onErrorResume { err ->
                                                            logger.error(err) { "Unhandled error processing event for correlationId=${event.correlationId}. Releasing lock." }
                                                            // liberta lock para retry posterior
                                                            paymentEventRepository.save(
                                                                event.copy(
                                                                    status = PaymentEventStatus.PENDING,
                                                                    owner = null,
                                                                    processingAt = null
                                                                )
                                                            ).then()
                                                        }
                                                }
                                            }, maxConcurrency)
                                            .then()
                                    }
                            }
                    }
            }
            .doOnError { e -> logger.error(e) { "Erro durante claimAndProcessBatch" } }
            .onErrorResume { Mono.empty() }
    }

}
