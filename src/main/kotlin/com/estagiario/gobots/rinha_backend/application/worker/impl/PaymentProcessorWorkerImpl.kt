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
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

@Component
class PaymentProcessorWorkerImpl(
    private val processorClient: ProcessorClient,
    private val paymentRepository: PaymentRepository,
    private val paymentEventRepository: PaymentEventRepository,
    @Value("\${payment.circuit-breaker.request-timeout-seconds:4}") private val requestTimeoutSeconds: Long
) : PaymentProcessorWorker {

    private val MAX_ATTEMPTS = 3

    override fun processPaymentFromQueue(event: PaymentEvent, payment: Payment): Mono<Void> {
        //val correlationId = payment.correlationId

        if (payment.status.isFinal()) {
            return markEventAsProcessed(event)
        }

        val processingPayment = payment.copy(
            status = PaymentStatus.PROCESSANDO,
            lastUpdatedAt = Instant.now(),
            attemptCount = payment.attemptCount + 1
        )

        return paymentRepository.save(processingPayment)
            .flatMap { currentPayment ->
                tryProcessor("default", currentPayment)
                    .switchIfEmpty(Mono.defer { tryProcessor("fallback", currentPayment) })
            }
            .flatMap { markEventAsProcessed(event) }
            .then()
    }

    private fun tryProcessor(processorName: String, payment: Payment): Mono<Payment> {
        val request = ProcessorPaymentRequest.fromPayment(payment)
        val processorCall = if (processorName == "default") processorClient.processPaymentDefault(request) else processorClient.processPaymentFallback(request)

        return processorCall
            .timeout(java.time.Duration.ofSeconds(requestTimeoutSeconds))
            .flatMap {
                val successPayment = payment.copy(status = PaymentStatus.SUCESSO, processorUsed = processorName, lastUpdatedAt = Instant.now())
                paymentRepository.save(successPayment)
            }
            .onErrorResume { error ->
                val statusCode = (error as? WebClientResponseException)?.statusCode?.value() ?: 0
                val paymentException = ProcessorPaymentException(error.message ?: "Erro desconhecido", statusCode, error)
                handleProcessingFailure(payment, paymentException).then(Mono.empty())
            }
    }

    private fun handleProcessingFailure(payment: Payment, exception: ProcessorPaymentException): Mono<Void> {
        val nextStatus = if (exception.isRetryable() && payment.attemptCount < MAX_ATTEMPTS) PaymentStatus.AGENDADO_RETRY else PaymentStatus.FALHA
        val updatedPayment = payment.copy(
            status = nextStatus,
            lastErrorMessage = exception.message,
            lastUpdatedAt = Instant.now(),
            nextRetryAt = if (nextStatus == PaymentStatus.AGENDADO_RETRY) Instant.now().plusSeconds((2.seconds.inWholeSeconds.shl(payment.attemptCount - 1))) else null
        )
        return paymentRepository.save(updatedPayment).then()
    }

    private fun markEventAsProcessed(event: PaymentEvent): Mono<Void> {
        return paymentEventRepository.save(event.copy(status = PaymentEventStatus.PROCESSED, processedAt = Instant.now())).then()
    }
}
