package com.estagiario.gobots.rinha_backend.application.worker

import com.estagiario.gobots.rinha_backend.domain.PaymentEvent
import com.estagiario.gobots.rinha_backend.domain.PaymentEventStatus
import com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository.PaymentRepository
import com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository.PaymentEventRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

@Component
class OutboxRelay(
    private val mongoTemplate: ReactiveMongoTemplate,
    private val paymentEventRepository: PaymentEventRepository,
    private val paymentRepository: PaymentRepository,
    private val paymentProcessorWorker: PaymentProcessorWorker,
    @Value("\${app.instance-id:API-LOCAL}") private val instanceId: String,
    @Value("\${app.run-mode:api}") private val runMode: String,
    @Value("\${app.outbox.batch-size:10}") private val batchSize: Int,
    @Value("\${app.outbox.max-concurrency:4}") private val maxConcurrency: Int
) {

    @Scheduled(fixedDelayString = "\${app.outbox.processing-delay-ms:500}")
    fun scheduleOutboxProcessing() {
        if (runMode != "worker" && runMode != "hybrid") return

        claimAndProcessBatch()
            .onErrorResume { Mono.empty() }
            .subscribe()
    }

    private fun claimAndProcessBatch(): Mono<Void> {
        return Flux.range(1, batchSize)
            .concatMap { claimOneEvent() }
            .filter { it != null }
            .cast(PaymentEvent::class.java)
            .collectList()
            .flatMap { claimedEvents ->
                if (claimedEvents.isEmpty()) {
                    return@flatMap Mono.empty<Void>()
                }

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

    private fun processEvent(event: PaymentEvent, paymentsMap: Map<String, com.estagiario.gobots.rinha_backend.domain.Payment>): Mono<Void> {
        val payment = paymentsMap[event.correlationId]

        return if (payment == null) {
            paymentEventRepository.save(
                event.copy(
                    status = PaymentEventStatus.PROCESSED,
                    processedAt = Instant.now()
                )
            ).then()
        } else {
            paymentProcessorWorker.processPaymentFromQueue(event, payment)
                .onErrorResume {
                    paymentEventRepository.save(
                        event.copy(
                            status = PaymentEventStatus.PENDING,
                            owner = null,
                            processingAt = null
                        )
                    ).then()
                }
        }
    }

    private fun claimOneEvent(): Mono<PaymentEvent?> {
        val now = Instant.now()
        val query = Query()
            .addCriteria(Criteria.where("status").`is`(PaymentEventStatus.PENDING))
            .with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "createdAt"))
            .limit(1)

        val update = Update()
            .set("status", PaymentEventStatus.PROCESSING)
            .set("owner", instanceId)
            .set("processingAt", now)

        val options = FindAndModifyOptions.options().returnNew(true)

        return mongoTemplate.findAndModify(query, update, options, PaymentEvent::class.java)
    }
}