package com.estagiario.gobots.rinha_backend.application.worker

import com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository.PaymentEventRepository
import com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository.PaymentRepository
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import mu.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class OutboxRelay(
    private val paymentEventRepository: PaymentEventRepository,
    private val paymentRepository: PaymentRepository,
    private val paymentProcessorWorker: PaymentProcessorWorker
) {

    private val logger = KotlinLogging.logger {}

    @Scheduled(fixedDelay = 200) // Verifica por novos eventos a cada 200ms
    suspend fun processOutboxEvents() {
        val pendingEvents = paymentEventRepository.findPendingAndMarkAsProcessing(50, "relay_worker")

        if (pendingEvents.isNotEmpty()) {
            logger.info { "Encontrados ${pendingEvents.size} eventos no Outbox para processar." }

            // Otimização: busca todos os payments de uma vez
            val correlationIds = pendingEvents.map { it.correlationId }
            val paymentsMap = paymentRepository.findAllById(correlationIds)
                .collectList().block()?.associateBy { it.correlationId } ?: emptyMap()

            coroutineScope {
                pendingEvents.forEach { event ->
                    val payment = paymentsMap[event.correlationId]
                    if (payment != null) {
                        launch {
                            paymentProcessorWorker.processPaymentFromQueue(event, payment)
                        }
                    } else {
                        logger.warn { "Payment para o evento ${event.id} (correlationId=${event.correlationId}) não encontrado. Marcando evento como falho." }
                        // Lidar com o caso de evento órfão
                    }
                }
            }
        }
    }
}