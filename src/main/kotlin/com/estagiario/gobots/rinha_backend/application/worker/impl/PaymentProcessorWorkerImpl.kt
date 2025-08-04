// ATUALIZE ESTE FICHEIRO:
// src/main/kotlin/com/estagiario/gobots/rinha_backend/application/worker/impl/PaymentProcessorWorkerImpl.kt

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
import kotlinx.coroutines.reactor.awaitSingleOrNull
import mu.KotlinLogging
import org.springframework.stereotype.Component
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
        // ✅ CORREÇÃO: Espera o Mono<Payment> ser resolvido para um objeto Payment
        val currentPayment = paymentRepository.save(processingPayment).awaitSingle()

        if (tryProcessor("default", currentPayment)) {
            markEventAsProcessed(event)
            return
        }

        if (tryProcessor("fallback", currentPayment)) {
            markEventAsProcessed(event)
            return
        }
    }

    private suspend fun tryProcessor(processorName: String, payment: Payment): Boolean {
        return try {
            val request = ProcessorPaymentRequest.fromPayment(payment)
            val processorCall = if (processorName == "default") processorClient::processPaymentDefault else processorClient::processPaymentFallback
            processorCall(request)

            val successPayment = payment.copy(
                status = PaymentStatus.SUCESSO,
                processorUsed = processorName,
                lastUpdatedAt = Instant.now()
            )
            // ✅ CORREÇÃO: Espera o Mono<Payment> ser salvo
            paymentRepository.save(successPayment).awaitSingle()
            logger.info { "Pagamento ${payment.correlationId} SUCESSO via $processorName" }
            true
        } catch (e: Exception) {
            logger.warn(e) { "Falha ao processar ${payment.correlationId} via $processorName" }
            val paymentException = if (e is ProcessorPaymentException) e else ProcessorPaymentException("Network/Timeout Error", 0, e)
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
        // ✅ CORREÇÃO: Espera o Mono<Payment> ser salvo
        paymentRepository.save(updatedPayment).awaitSingle()
        logger.warn { "Pagamento ${payment.correlationId} movido para ${updatedPayment.status} após falha." }
    }

    private suspend fun markEventAsProcessed(event: PaymentEvent) {
        // ✅ CORREÇÃO: Espera o Mono<PaymentEvent> ser salvo
        paymentEventRepository.save(
            event.copy(status = PaymentEventStatus.PROCESSED, processedAt = Instant.now())
        ).awaitSingle()
    }
}