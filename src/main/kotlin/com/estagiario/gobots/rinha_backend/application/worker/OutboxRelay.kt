package com.estagiario.gobots.rinha_backend.application.worker

import com.estagiario.gobots.rinha_backend.domain.PaymentEventStatus
import com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository.PaymentEventRepository
import com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository.PaymentRepository
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.toList
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
        // CORRIGIDO: Usa o método que realmente existe no repositório
        val pendingEvents = paymentEventRepository.findAllByStatus(PaymentEventStatus.PENDING).toList()

        if (pendingEvents.isNotEmpty()) {
            logger.info { "Encontrados ${pendingEvents.size} eventos no Outbox para processar." }

            val correlationIds = pendingEvents.map { it.correlationId }
            // CORRIGIDO: Usa .toList() para converter o Flow em uma lista de forma não-bloqueante
            val paymentsMap = paymentRepository.findAllByCorrelationIdIn(correlationIds)
                .toList()
                .associateBy { it.correlationId }

            coroutineScope {
                pendingEvents.forEach { event ->
                    val payment = paymentsMap[event.correlationId]
                    if (payment != null) {
                        launch {
                            paymentProcessorWorker.processPaymentFromQueue(event, payment)
                        }
                    } else {
                        logger.warn { "Payment para o evento ${event.id} (correlationId=${event.correlationId}) não encontrado." }
                        // TODO: Lidar com o caso de evento órfão (ex: mover para uma "dead letter queue")
                    }
                }
            }
        }
    }
}