package com.estagiario.gobots.rinha_backend.application.worker

import com.estagiario.gobots.rinha_backend.domain.Payment
import com.estagiario.gobots.rinha_backend.domain.PaymentEvent
import com.estagiario.gobots.rinha_backend.domain.PaymentEventStatus
import com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository.PaymentEventRepository
import com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository.PaymentRepository
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.FindAndModifyOptions
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
    @Value("\${app.outbox.max-concurrency:4}") private val maxConcurrency: Int,
    @Value("\${app.outbox.lock-timeout-minutes:5}") private val lockTimeoutMinutes: Long
) {

    @Scheduled(fixedDelayString = "\${app.outbox.processing-delay-ms:500}")
    fun scheduleOutboxProcessing() {
        if (runMode != "worker" && runMode != "hybrid") return

        claimAndProcessBatch()
            .doOnError { e -> logger.error(e) { "Error during outbox processing batch" } }
            .onErrorResume { Mono.empty() }
            .subscribe()
    }

    private fun claimAndProcessBatch(): Mono<Void> {
        return Flux.range(1, batchSize)
            .concatMap { claimOneEvent() }
            .takeWhile { it != null }
            .cast(PaymentEvent::class.java)
            .collectList()
            .flatMap { claimedEvents ->
                if (claimedEvents.isEmpty()) {
                    return@flatMap Mono.empty<Void>()
                }
                logger.debug { "Claimed ${claimedEvents.size} events for processing" }
                val correlationIds = claimedEvents.map { it.correlationId }.distinct()
                paymentRepository.findAllByCorrelationIdIn(correlationIds)
                    .collectMap { it.correlationId }
                    .flatMap { paymentsMap ->
                        Flux.fromIterable(claimedEvents)
                            .flatMap(
                                { event -> processEvent(event, paymentsMap) },
                                maxConcurrency
                            )
                            .then()
                    }
            }
    }

    private fun processEvent(event: PaymentEvent, paymentsMap: Map<String, Payment>): Mono<Void> {
        val payment = paymentsMap[event.correlationId]
        return if (payment == null) {
            logger.warn { "Payment not found for event with correlationId=${event.correlationId}. Marking event as processed to avoid retry loops." }
            paymentEventRepository.save(
                event.copy(
                    status = PaymentEventStatus.PROCESSED,
                    processedAt = Instant.now()
                )
            ).then()
        } else {
            if (event.status != PaymentEventStatus.PROCESSING || event.owner != instanceId) {
                logger.warn {
                    "Event ${event.id} for correlationId=${event.correlationId} is not properly claimed by this instance. " +
                            "Status=${event.status}, Owner=${event.owner}, Expected=${instanceId}. Skipping."
                }
                return Mono.empty()
            }
            paymentProcessorWorker.processPaymentFromQueue(event, payment)
                .onErrorResume { error ->
                    logger.error(error) { "Unhandled error processing event for correlationId=${event.correlationId}. Releasing lock." }
                    releaseEventLock(event)
                }
        }
    }

    private fun claimOneEvent(): Mono<PaymentEvent?> {
        val now = Instant.now()
        val lockExpiredThreshold = now.minus(lockTimeoutMinutes, ChronoUnit.MINUTES)

        val query = Query()
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
            .limit(1)

        val update = Update()
            .set("status", PaymentEventStatus.PROCESSING)
            .set("owner", instanceId)
            .set("processingAt", now)

        val options = FindAndModifyOptions.options().returnNew(true)

        return mongoTemplate.findAndModify(query, update, options, PaymentEvent::class.java)
            .doOnNext { event ->
                logger.debug { "Successfully claimed event ${event.id} for correlationId=${event.correlationId}" }
            }
            .onErrorResume { error ->
                logger.debug { "Failed to claim event (normal in concurrent environment): ${error.message}" }
                Mono.empty()
            }
    }

    private fun releaseEventLock(event: PaymentEvent): Mono<Void> {
        return paymentEventRepository.save(
            event.copy(
                status = PaymentEventStatus.PENDING,
                owner = null,
                processingAt = null
            )
        )
            .doOnSuccess {
                logger.debug { "Released lock for event ${event.id} correlationId=${event.correlationId}" }
            }
            .then()
    }
}