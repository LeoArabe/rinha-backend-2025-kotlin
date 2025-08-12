package com.estagiario.gobots.rinha_backend.application.worker.impl

import com.estagiario.gobots.rinha_backend.application.client.ProcessorClient
import com.estagiario.gobots.rinha_backend.application.worker.PaymentProcessorWorker
import com.estagiario.gobots.rinha_backend.domain.Payment
import com.estagiario.gobots.rinha_backend.domain.PaymentEvent
import com.estagiario.gobots.rinha_backend.domain.PaymentEventStatus
import com.estagiario.gobots.rinha_backend.domain.PaymentStatus
import com.estagiario.gobots.rinha_backend.infrastructure.client.ProcessorPaymentException
import com.estagiario.gobots.rinha_backend.infrastructure.client.dto.ProcessorPaymentRequest
import com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository.PaymentEventRepository
import com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository.PaymentRepository
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.Instant
import kotlin.math.pow

private val logger = KotlinLogging.logger {}

@Component
class PaymentProcessorWorkerImpl(
    private val processorClient: ProcessorClient,
    private val paymentRepository: PaymentRepository,
    private val paymentEventRepository: PaymentEventRepository,
    @Value("\${payment.circuit-breaker.request-timeout-seconds:4}") private val requestTimeoutSeconds: Long,
    @Value("\${payment.retry.max-attempts:2}") private val maxAttempts: Int,
    @Value("\${payment.retry.max-backoff-seconds:30}") private val maxBackoffSeconds: Long
) : PaymentProcessorWorker {

    override fun processPaymentFromQueue(event: PaymentEvent, payment: Payment): Mono<Void> {
        if (event.status != PaymentEventStatus.PROCESSING) {
            logger.warn {
                "Event ${event.id} for correlationId=${payment.correlationId} is not in PROCESSING state. Current status: ${event.status}. Skipping."
            }
            return Mono.empty()
        }

        if (payment.status.isFinal()) {
            logger.info { "Payment for correlationId=${payment.correlationId} is already in a final state (${payment.status}). Skipping." }
            return markEventAsProcessed(event)
        }

        val processingPayment = payment.copy(
            status = PaymentStatus.PROCESSANDO,
            lastUpdatedAt = Instant.now(),
            attemptCount = payment.attemptCount + 1
        )

        return paymentRepository.save(processingPayment)
            .flatMap { currentPayment ->
                tryProcessor("default", currentPayment, event)
                    .switchIfEmpty(Mono.defer { tryProcessor("fallback", currentPayment, event) })
            }
            .flatMap { successfulPayment ->
                logger.info {
                    "Payment successfully processed for correlationId=${successfulPayment.correlationId} " +
                            "using processor=${successfulPayment.processorUsed}"
                }
                markEventAsProcessed(event)
            }
            .then()
    }

    private fun tryProcessor(processorName: String, payment: Payment, event: PaymentEvent): Mono<Payment> {
        val request = ProcessorPaymentRequest.fromPayment(payment)
        val processorCall = if (processorName == "default")
            processorClient.processPaymentDefault(request)
        else
            processorClient.processPaymentFallback(request)

        return processorCall
            .timeout(Duration.ofSeconds(requestTimeoutSeconds))
            .flatMap {
                val successPayment = payment.copy(
                    status = PaymentStatus.SUCESSO,
                    processorUsed = processorName,
                    lastUpdatedAt = Instant.now()
                )
                paymentRepository.save(successPayment)
            }
            .onErrorResume { error ->
                logger.warn {
                    "Processor '$processorName' failed for correlationId=${payment.correlationId}: ${error.message}"
                }

                val statusCode = when (error) {
                    is ProcessorPaymentException -> error.httpStatusCode
                    is WebClientResponseException -> error.statusCode.value()
                    else -> 0
                }

                val message = error.message ?: "Erro desconhecido"
                val paymentException = ProcessorPaymentException(message, statusCode, error)

                handleProcessingFailure(payment, event, paymentException, processorName)
                    .then(Mono.empty<Payment>())
            }
    }

    private fun handleProcessingFailure(
        payment: Payment,
        event: PaymentEvent,
        exception: ProcessorPaymentException,
        failedProcessor: String
    ): Mono<Void> {
        val isRetryable = exception.isRetryable() && payment.attemptCount < maxAttempts
        val nextStatus = if (isRetryable) PaymentStatus.AGENDADO_RETRY else PaymentStatus.FALHA

        val nextRetryAt = if (nextStatus == PaymentStatus.AGENDADO_RETRY) {
            val backoffSeconds = minOf(
                2.0.pow(payment.attemptCount.toDouble()).toLong(),
                maxBackoffSeconds
            )
            Instant.now().plusSeconds(backoffSeconds)
        } else {
            null
        }

        val updatedPayment = payment.copy(
            status = nextStatus,
            lastErrorMessage = exception.message,
            lastUpdatedAt = Instant.now(),
            nextRetryAt = nextRetryAt,
            attemptCount = payment.attemptCount + 1
        )

        val updatedEventMono: Mono<Void> = if (isRetryable) {
            paymentEventRepository.save(
                event.copy(
                    status = PaymentEventStatus.PENDING,
                    owner = null,
                    processingAt = null,
                    nextRetryAt = nextRetryAt
                )
            ).then()
        } else {
            markEventAsProcessed(event)
        }

        return paymentRepository.save(updatedPayment)
            .then(updatedEventMono)
    }

    private fun markEventAsProcessed(event: PaymentEvent): Mono<Void> {
        return paymentEventRepository.save(
            event.copy(
                status = PaymentEventStatus.PROCESSED,
                processedAt = Instant.now()
            )
        )
            .doOnSuccess {
                logger.debug { "Marked event ${event.id} as processed for correlationId=${event.correlationId}" }
            }
            .then()
    }
}
