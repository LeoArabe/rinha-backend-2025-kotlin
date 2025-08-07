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
import kotlinx.coroutines.reactor.awaitSingle
import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

@Component
class PaymentProcessorWorkerImpl(
    private val processorClient: ProcessorClient,
    private val paymentRepository: PaymentRepository,
    private val paymentEventRepository: PaymentEventRepository
) : PaymentProcessorWorker {

    private val logger = KotlinLogging.logger {}
    private val MAX_ATTEMPTS = 3

    override suspend fun processPaymentFromQueue(event: PaymentEvent, payment: Payment) {
        val correlationId = payment.correlationId

        if (payment.status.isFinal()) {
            logger.warn { "message=\"Evento ignorado, pagamento em estado final\" correlationId=$correlationId currentStatus=${payment.status}" }
            markEventAsProcessed(event)
            return
        }

        logger.info { "message=\"Iniciando processamento de pagamento\" correlationId=$correlationId attempt=${payment.attemptCount + 1}" }

        val processingPayment = payment.copy(
            status = PaymentStatus.PROCESSANDO,
            lastUpdatedAt = Instant.now(),
            attemptCount = payment.attemptCount + 1
        )
        val currentPayment = paymentRepository.save(processingPayment).awaitSingle()

        if (tryProcessor("default", currentPayment)) {
            markEventAsProcessed(event)
            return
        }

        logger.warn { "message=\"Processador default falhou, tentando fallback\" correlationId=$correlationId" }

        if (tryProcessor("fallback", currentPayment)) {
            markEventAsProcessed(event)
            return
        }
    }

    // ✅ ÚNICA VERSÃO CORRETA DO MÉTODO
    private suspend fun tryProcessor(processorName: String, payment: Payment): Boolean {
        val correlationId = payment.correlationId
        return try {
            val request = ProcessorPaymentRequest.fromPayment(payment)

            val processorCall = if (processorName == "default") processorClient::processPaymentDefault else processorClient::processPaymentFallback
            processorCall(request)

            val successPayment = payment.copy(
                status = PaymentStatus.SUCESSO,
                processorUsed = processorName,
                lastUpdatedAt = Instant.now()
            )
            paymentRepository.save(successPayment).awaitSingle()

            logger.info { "message=\"Pagamento processado com sucesso\" correlationId=$correlationId processor=$processorName" }
            true
        } catch (e: Exception) {
            logger.warn(e) { "message=\"Falha na chamada ao processador externo\" correlationId=$correlationId processor=$processorName error=\"${e.message}\"" }

            // Lógica melhorada para tratar o erro 422
            val statusCode = (e as? WebClientResponseException)?.statusCode?.value() ?: 0
            val paymentException = ProcessorPaymentException(e.message ?: "Erro desconhecido", statusCode, e)

            handleProcessingFailure(payment, paymentException)
            false
        }
    }

    private suspend fun handleProcessingFailure(payment: Payment, exception: ProcessorPaymentException) {
        val nextStatus = if (exception.isRetryable() && payment.attemptCount < MAX_ATTEMPTS) {
            PaymentStatus.AGENDADO_RETRY
        } else {
            PaymentStatus.FALHA
        }

        val updatedPayment = payment.copy(
            status = nextStatus,
            lastErrorMessage = exception.message,
            lastUpdatedAt = Instant.now(),
            nextRetryAt = if (nextStatus == PaymentStatus.AGENDADO_RETRY) {
                val delaySeconds = (2.seconds.inWholeSeconds.shl(payment.attemptCount - 1))
                Instant.now().plusSeconds(delaySeconds)
            } else {
                null
            }
        )
        paymentRepository.save(updatedPayment).awaitSingle()
        logger.warn { "message=\"Pagamento movido para status final\" correlationId=${payment.correlationId} newStatus=$nextStatus reason=\"${exception.message}\"" }
    }

    private suspend fun markEventAsProcessed(event: PaymentEvent) {
        paymentEventRepository.save(
            event.copy(status = PaymentEventStatus.PROCESSED, processedAt = Instant.now())
        ).awaitSingle()
    }
}