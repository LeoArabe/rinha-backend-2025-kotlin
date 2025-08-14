package com.estagiario.gobots.rinha_backend.application.worker.impl

import com.estagiario.gobots.rinha_backend.application.service.PaymentService
import com.estagiario.gobots.rinha_backend.application.worker.OutboxRelay
import com.estagiario.gobots.rinha_backend.domain.Payment
import com.estagiario.gobots.rinha_backend.domain.PaymentEvent
import com.estagiario.gobots.rinha_backend.domain.PaymentStatus
import com.estagiario.gobots.rinha_backend.infrastructure.client.dto.ProcessorPaymentRequest
import com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository.PaymentRepository
import jakarta.annotation.PostConstruct
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant

private val logger = KotlinLogging.logger {}

@Component
class PaymentProcessorWorkerImpl(
    private val outboxRelay: OutboxRelay,
    private val paymentRepository: PaymentRepository,
    private val paymentService: PaymentService,
    private val webClientBuilder: WebClient.Builder,
    @Value("\${app.run-mode:api}") private val runMode: String,
    @Value("\${app.instance-id:WORKER-LOCAL}") private val instanceId: String,
    @Value("\${app.outbox.batch-size:10}") private val batchSize: Long,
    @Value("\${app.outbox.processing-delay-ms:120}") private val processingDelayMs: Long,
    @Value("\${payment.retry.max-attempts:2}") private val maxAttempts: Int,
    @Value("\${payment.request-timeout-seconds:4}") private val requestTimeoutSeconds: Long,
    @Value("\${app.processor.default.url}") private val defaultUrl: String,
    @Value("\${app.processor.fallback.url}") private val fallbackUrl: String
) {
    private val defaultClient by lazy { webClientBuilder.baseUrl(defaultUrl).build() }
    private val fallbackClient by lazy { webClientBuilder.baseUrl(fallbackUrl).build() }

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
                val request = ProcessorPaymentRequest(
                    correlationId = payment.correlationId,
                    amount = BigDecimal(payment.amount).movePointLeft(2).setScale(2, RoundingMode.HALF_UP),
                    requestedAt = payment.requestedAt
                )

                callProcessor(defaultClient, request, "default")
                    .onErrorResume {
                        callProcessor(fallbackClient, request, "fallback")
                    }
                    .flatMap { processor ->
                        paymentService.updatePaymentStatus(payment, PaymentStatus.SUCCESS, processor, null)
                            .then(outboxRelay.markAsProcessed(event))
                    }
                    .onErrorResume { error: Throwable ->
                        handleProcessingError(event, error)
                    }
            }
            .then()
    }

    private fun callProcessor(client: WebClient, request: ProcessorPaymentRequest, processorName: String): Mono<String> {
        return client.post().uri("/payments").bodyValue(request).retrieve()
            .bodyToMono<Map<String, Any>>()
            .timeout(Duration.ofSeconds(requestTimeoutSeconds))
            .thenReturn(processorName)
    }

    private fun handleProcessingError(event: PaymentEvent, error: Throwable): Mono<PaymentEvent> {
        val newAttemptCount = event.attemptCount + 1
        if (newAttemptCount >= maxAttempts) {
            logger.error(error) { "Final attempt failed for ${event.correlationId}. Marking as FAILED." }
            return paymentRepository.findByCorrelationId(event.correlationId)
                .flatMap { payment ->
                    paymentService.updatePaymentStatus(payment, PaymentStatus.FAILURE, "none", error.message)
                }
                // ✅ CORREÇÃO: Usa flatMap para manter o tipo PaymentEvent na cadeia reativa
                .flatMap { outboxRelay.markAsProcessed(event) }
        } else {
            val delay = 2L.shl(newAttemptCount - 1)
            logger.warn(error) { "Attempt $newAttemptCount for ${event.correlationId}. Retrying in ${delay}s." }
            return outboxRelay.scheduleForRetry(event, delay, error.message)
        }
    }
}