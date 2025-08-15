package com.estagiario.gobots.rinha_backend.application.worker.impl

import com.estagiario.gobots.rinha_backend.application.ports.PaymentProcessorClient
import com.estagiario.gobots.rinha_backend.application.service.PaymentService
import com.estagiario.gobots.rinha_backend.application.worker.OutboxRelay
import com.estagiario.gobots.rinha_backend.domain.PaymentEvent
import com.estagiario.gobots.rinha_backend.domain.PaymentStatus
import com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository.PaymentRepository
import jakarta.annotation.PostConstruct
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration

private val logger = KotlinLogging.logger {}

@Component
class PaymentProcessorWorkerImpl(
    private val outboxRelay: OutboxRelay,
    private val paymentRepository: PaymentRepository,
    private val paymentService: PaymentService,
    @Qualifier("defaultClient") private val defaultClient: PaymentProcessorClient,
    @Qualifier("fallbackClient") private val fallbackClient: PaymentProcessorClient,
    @Value("\${app.run-mode:api}") private val runMode: String,
    @Value("\${app.instance-id:WORKER-LOCAL}") private val instanceId: String,
    @Value("\${app.outbox.batch-size:10}") private val batchSize: Long,
    @Value("\${app.outbox.processing-delay-ms:120}") private val processingDelayMs: Long,
    @Value("\${payment.retry.max-attempts:2}") private val maxAttempts: Int
) {

    @PostConstruct
    fun startProcessingLoop() {
        if (!runMode.contains("worker", ignoreCase = true) && !runMode.contains("hybrid", ignoreCase = true)) {
            return
        }

        Flux.interval(Duration.ofMillis(processingDelayMs))
            .flatMap { _ -> outboxRelay.claimBatch(instanceId, batchSize.toInt()) }
            .flatMap { event -> processSingleEvent(event) }
            .onErrorContinue { err, _ -> logger.error(err) { "Unhandled error in worker loop" } }
            .subscribe()
    }

    private fun processSingleEvent(event: PaymentEvent): Mono<Void> {
        return paymentRepository.findByCorrelationId(event.correlationId)
            .flatMap { payment ->
                defaultClient.process(payment.correlationId, payment.toBigDecimal(), payment.requestedAt)
                    .thenReturn("default")
                    .onErrorResume {
                        fallbackClient.process(payment.correlationId, payment.toBigDecimal(), payment.requestedAt)
                            .thenReturn("fallback")
                    }
                    .flatMap { processor ->
                        paymentService.updatePaymentStatus(payment, PaymentStatus.SUCCESS, processor)
                            .then(outboxRelay.markAsProcessed(event))
                            .then()
                    }
                    .onErrorResume { error ->
                        handleProcessingError(event, error).then()
                    }
            }
            .then()
    }

    private fun handleProcessingError(event: PaymentEvent, error: Throwable): Mono<PaymentEvent> {
        val newAttemptCount = event.attemptCount + 1
        if (newAttemptCount >= maxAttempts) {
            logger.error(error) { "Final attempt failed for ${event.correlationId}. Marking as FAILED." }
            return paymentRepository.findByCorrelationId(event.correlationId)
                .flatMap { payment ->
                    paymentService.updatePaymentStatus(payment, PaymentStatus.FAILURE, "none", error.message)
                }
                .flatMap { outboxRelay.markAsProcessed(event) }
        } else {
            val delay = 1L.shl(newAttemptCount)
            logger.warn(error) { "Attempt $newAttemptCount for ${event.correlationId}. Retrying in ${delay}s." }
            return outboxRelay.scheduleForRetry(event, delay, error.message)
        }
    }
}