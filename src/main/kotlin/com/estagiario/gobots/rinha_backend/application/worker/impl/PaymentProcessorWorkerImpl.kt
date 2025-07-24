package com.estagiario.gobots.rinha_backend.application.worker.impl

import com.estagiario.gobots.rinha_backend.application.client.ProcessorClient
import com.estagiario.gobots.rinha_backend.application.worker.PaymentProcessorWorker
import com.estagiario.gobots.rinha_backend.domain.Payment
import com.estagiario.gobots.rinha_backend.domain.PaymentEvent
import com.estagiario.gobots.rinha_backend.domain.PaymentEventStatus
import com.estagiario.gobots.rinha_backend.domain.PaymentStatus
import com.estagiario.gobots.rinha_backend.infrastructure.client.dto.ProcessorPaymentRequest
import com.estagiario.gobots.rinha_backend.infrastructure.client.dto.ProcessorPaymentResponse
import com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository.PaymentEventRepository
import com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository.PaymentRepository
import mu.KotlinLogging
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class PaymentProcessorWorkerImpl(
    private val processorClient: ProcessorClient,
    private val paymentRepository: PaymentRepository,
    private val paymentEventRepository: PaymentEventRepository
) : PaymentProcessorWorker {

    private val logger = KotlinLogging.logger {}

    override suspend fun processPaymentFromQueue(event: PaymentEvent, payment: Payment) {
        if (payment.status.isFinal()) {
            logger.warn { "Pagamento ${payment.correlationId} já está em estado final (${payment.status}). Pulando." }
            paymentEventRepository.save(event.copy(status = PaymentEventStatus.PROCESSED, processedAt = Instant.now()))
            return
        }

        val currentPayment = payment.copy(status = PaymentStatus.PROCESSANDO, lastUpdatedAt = Instant.now())
        paymentRepository.save(currentPayment)

        try {
            val useDefault = true // TODO: Substituir por lógica de leitura de saúde do Redis

            if (useDefault) {
                processWith(currentPayment, "default", processorClient::processPaymentDefault)
            } else {
                processWith(currentPayment, "fallback", processorClient::processPaymentFallback)
            }

        } catch (e: Exception) {
            val failedPayment = currentPayment.copy(status = PaymentStatus.FALHA, lastErrorMessage = e.message, lastUpdatedAt = Instant.now())
            paymentRepository.save(failedPayment)
        } finally {
            paymentEventRepository.save(event.copy(status = PaymentEventStatus.PROCESSED, processedAt = Instant.now()))
        }
    }

    private suspend fun processWith(
        payment: Payment,
        processorName: String,
        processorCall: suspend (ProcessorPaymentRequest) -> ProcessorPaymentResponse
    ) {
        try {
            // TODO: Envolver esta chamada com o CircuitBreakerManager
            val request = ProcessorPaymentRequest.fromPayment(payment)
            processorCall(request)

            val successPayment = payment.copy(
                status = PaymentStatus.SUCESSO,
                processorUsed = processorName,
                lastUpdatedAt = Instant.now()
            )
            paymentRepository.save(successPayment)
            logger.info { "Pagamento ${payment.correlationId} processado com SUCESSO via $processorName" }

        } catch (e: Exception) {
            // TODO: Adicionar lógica de retry com backoff aqui
            logger.warn(e) { "Falha ao processar ${payment.correlationId} via $processorName. Tentando fallback." }

            if (processorName == "default") {
                processWith(payment, "fallback", processorClient::processPaymentFallback)
            } else {
                val failedPayment = payment.copy(status = PaymentStatus.FALHA, lastErrorMessage = e.message, lastUpdatedAt = Instant.now())
                paymentRepository.save(failedPayment)
            }
        }
    }
}