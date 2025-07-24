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

    @Scheduled(fixedDelay = 200)
    suspend fun processOutboxEvents() {
        val pendingEvents = paymentEventRepository.findAllByStatus(PaymentEventStatus.PENDING).toList()

        if (pendingEvents.isNotEmpty()) {
            logger.info { "Encontrados ${pendingEvents.size} eventos no Outbox para processar." }

            val correlationIds = pendingEvents.map { it.correlationId }
            val paymentsMap = paymentRepository.findAllByCorrelationIdIn(correlationIds)
                .toList()
                .associateBy { it.correlationId }

            coroutineScope {
                pendingEvents.forEach { event ->
                    paymentsMap[event.correlationId]?.let { payment ->
                        launch {
                            paymentProcessorWorker.processPaymentFromQueue(event, payment)
                        }
                    } ?: logger.warn { "Payment para o evento ${event.id} (correlationId=${event.correlationId}) não encontrado." }
                }
            }
        }
    }
}