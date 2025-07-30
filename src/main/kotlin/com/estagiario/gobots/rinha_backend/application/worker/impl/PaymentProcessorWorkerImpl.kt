// Caminho: src/main/kotlin/com/estagiario/gobots/rinha_backend/application/worker/impl/PaymentProcessorWorkerImpl.kt
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
import org.springframework.stereotype.Component
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

@Component
class PaymentProcessorWorkerImpl(
    private val processorClient: ProcessorClient,
    private val paymentRepository: PaymentRepository,
    private val paymentEventRepository: PaymentEventRepository
    // TODO: Injete aqui seu CircuitBreakerManager e as configurações do application.yml
) : PaymentProcessorWorker {

    private val logger = KotlinLogging.logger {}
    private val MAX_ATTEMPTS = 3

    override suspend fun processPaymentFromQueue(event: PaymentEvent, payment: Payment) {
        if (payment.status.isFinal()) {
            logger.warn { "Pagamento ${payment.correlationId} já em estado final. Finalizando evento." }
            markEventAsProcessed(event)
            return
        }

        val processingPayment = payment.copy(
            status = PaymentStatus.PROCESSANDO,
            lastUpdatedAt = Instant.now(),
            attemptCount = payment.attemptCount + 1
        )
        val currentPayment = paymentRepository.save(processingPayment)

        // Tenta o processador default. Se for sucesso, o 'return' encerra a função.
        if (tryProcessor("default", currentPayment)) {
            markEventAsProcessed(event)
            return
        }

        // Se chegou aqui, o default falhou. Tenta o fallback.
        if (tryProcessor("fallback", currentPayment)) {
            markEventAsProcessed(event)
            return
        }

        // Se ambos falharam, a lógica de retry/falha já foi tratada.
        // Verificamos o estado atual do objeto (que foi modificado por handleProcessingFailure)
        // para decidir se o evento deve ser finalizado.
        if (currentPayment.status == PaymentStatus.FALHA) {
            markEventAsProcessed(event)
        }
        // Se o estado for AGENDADO_RETRY, NÃO marcamos, para que seja pego novamente pelo Relay.
    }

    private suspend fun tryProcessor(processorName: String, payment: Payment): Boolean {
        return try {
            // TODO: Envolver com CircuitBreaker
            val request = ProcessorPaymentRequest.fromPayment(payment)
            val processorCall = if (processorName == "default") processorClient::processPaymentDefault else processorClient::processPaymentFallback
            processorCall(request)

            // Sucesso!
            val successPayment = payment.copy(
                status = PaymentStatus.SUCESSO,
                processorUsed = processorName,
                lastUpdatedAt = Instant.now()
            )
            paymentRepository.save(successPayment)
            logger.info { "Pagamento ${payment.correlationId} SUCESSO via $processorName" }
            true // Retorna sucesso

        } catch (e: Exception) {
            logger.warn(e) { "Falha ao processar ${payment.correlationId} via $processorName" }
            // Encapsula exceções de rede/timeout para tratamento padronizado e robusto
            val paymentException = if (e is ProcessorPaymentException) e else ProcessorPaymentException("Network/Timeout Error", 0, e)
            handleProcessingFailure(payment, paymentException)
            false // Retorna falha
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
                // Backoff exponencial simples (2s, 4s, 8s...)
                val delaySeconds = (2.seconds.inWholeSeconds.shl(payment.attemptCount - 1))
                Instant.now().plusSeconds(delaySeconds)
            } else {
                null
            }
        )
        paymentRepository.save(updatedPayment)
        logger.warn { "Pagamento ${payment.correlationId} movido para ${updatedPayment.status} após falha." }
    }

    private suspend fun markEventAsProcessed(event: PaymentEvent) {
        paymentEventRepository.save(
            event.copy(status = PaymentEventStatus.PROCESSED, processedAt = Instant.now())
        )
    }
}