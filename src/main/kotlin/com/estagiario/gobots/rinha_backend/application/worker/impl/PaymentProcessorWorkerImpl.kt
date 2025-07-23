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
    // Adicionar CircuitBreakerManager e leitor de saúde do Redis
) : PaymentProcessorWorker {

    private val logger = KotlinLogging.logger {}

    override suspend fun processPaymentFromQueue(event: PaymentEvent, payment: Payment) {
        if (payment.status.isFinal()) {
            logger.warn { "Pagamento ${payment.correlationId} já está em estado final (${payment.status}). Pulando processamento." }
            // Marcar evento como processado para não ser pego novamente
            paymentEventRepository.save(event.copy(status = PaymentEventStatus.PROCESSED, processedAt = Instant.now())).subscribe()
            return
        }

        // 1. Marcar como PROCESSANDO para evitar trabalho duplicado
        var currentPayment = payment.copy(status = PaymentStatus.PROCESSANDO, lastUpdatedAt = Instant.now())
        paymentRepository.save(currentPayment).subscribe()

        try {
            // TODO: Adicionar lógica para ler a saúde do Redis aqui
            val useDefault = true // Simplificação, deve ser baseado na saúde lida do Redis

            if (useDefault) {
                processWith(currentPayment, "default", processorClient::processPaymentDefault)
            } else {
                processWith(currentPayment, "fallback", processorClient::processPaymentFallback)
            }

        } catch (e: Exception) {
            // Lógica de falha, agendamento de retry, etc.
            logger.error(e) { "Falha não tratada ao processar pagamento ${payment.correlationId}" }
            val failedPayment = currentPayment.copy(status = PaymentStatus.FALHA, lastErrorMessage = e.message, lastUpdatedAt = Instant.now())
            paymentRepository.save(failedPayment).subscribe()
        } finally {
            // Marcar evento como processado ao final de tudo
            paymentEventRepository.save(event.copy(status = PaymentEventStatus.PROCESSED, processedAt = Instant.now())).subscribe()
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

            // Sucesso!
            val successPayment = payment.copy(
                status = PaymentStatus.SUCESSO,
                processorUsed = processorName,
                lastUpdatedAt = Instant.now()
            )
            paymentRepository.save(successPayment).subscribe()
            logger.info { "Pagamento ${payment.correlationId} processado com SUCESSO via $processorName" }

        } catch (e: Exception) {
            // TODO: Adicionar lógica de retry com backoff aqui
            logger.warn(e) { "Falha ao processar pagamento ${payment.correlationId} via $processorName. Tentando fallback ou agendando retry." }

            // Tenta o fallback como exemplo simples de recuperação
            if (processorName == "default") {
                processWith(payment, "fallback", processorClient::processPaymentFallback)
            } else {
                // Se até o fallback falhou, marca como falha definitiva
                val failedPayment = payment.copy(status = PaymentStatus.FALHA, lastErrorMessage = e.message, lastUpdatedAt = Instant.now())
                paymentRepository.save(failedPayment).subscribe()
            }
        }
    }
}