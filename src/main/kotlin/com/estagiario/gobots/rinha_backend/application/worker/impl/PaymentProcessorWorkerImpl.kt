package com.estagiario.gobots.rinha_backend.application.worker.impl

import com.estagiario.gobots.rinha_backend.application.service.impl.PaymentServiceImpl
import com.estagiario.gobots.rinha_backend.application.worker.OutboxRelay
import com.estagiario.gobots.rinha_backend.domain.Payment
import com.estagiario.gobots.rinha_backend.domain.PaymentEvent
import com.estagiario.gobots.rinha_backend.domain.PaymentStatus
import com.estagiario.gobots.rinha_backend.infrastructure.client.dto.ProcessorPaymentRequest
import com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository.PaymentRepository
import jakarta.annotation.PostConstruct // ✅ IMPORT ADICIONADO
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
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
    private val paymentService: PaymentServiceImpl,
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
        if (!runMode.equals("hybrid", true) && !runMode.equals("worker", true)) {
            logger.info { "Worker disabled for run-mode '$runMode'" }
            return
        }

        logger.info { "Starting OutboxRelay worker loop with ownerId='$instanceId'" }

        Flux.interval(Duration.ofMillis(processingDelayMs))
            .flatMap { _ -> outboxRelay.claimBatch(instanceId, batchSize) }
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
                        logger.warn { "Default processor failed for ${payment.correlationId}, trying fallback..." }
                        callProcessor(fallbackClient, request, "fallback")
                    }
                    .flatMap { processor ->
                        paymentService.updatePaymentStatus(payment, PaymentStatus.SUCESSO, processor)
                            .flatMap { outboxRelay.markAsProcessed(event) } // ✅ FlatMap para manter o fluxo
                    }
                    .onErrorResume { error ->
                        handleProcessingError(event, payment, error) // ✅ Retorna Mono<PaymentEvent>
                    }
            }
            .then()
    }

    private fun callProcessor(client: WebClient, request: ProcessorPaymentRequest, processorName: String): Mono<String> {
        return client.post().uri("/payments").bodyValue(request).retrieve()
            .bodyToMono(String::class.java)
            .timeout(Duration.ofSeconds(requestTimeoutSeconds))
            .thenReturn(processorName)
    }

    private fun handleProcessingError(event: PaymentEvent, payment: Payment, error: Throwable): Mono<PaymentEvent> {
        val newAttemptCount = payment.attemptCount + 1
        if (newAttemptCount >= maxAttempts) {
            logger.error(error) { "Final attempt failed for ${payment.correlationId}. Marking as FALHA." }
            return paymentService.updatePaymentStatus(payment, PaymentStatus.FALHA, "all", error.message)
                .flatMap { outboxRelay.markAsProcessed(event) } // Para de processar
        } else {
            val delay = 2L.shl(newAttemptCount - 1) // Backoff: 2, 4, 8...
            logger.warn(error) { "Attempt $newAttemptCount failed for ${payment.correlationId}. Retrying in ${delay}s." }
            return paymentRepository.save(payment.copy(attemptCount = newAttemptCount))
                .flatMap { outboxRelay.scheduleForRetry(event, delay) } // ✅ Passa o Long diretamente
        }
    }
}