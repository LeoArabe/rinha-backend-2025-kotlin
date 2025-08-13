package com.estagiario.gobots.rinha_backend.application.worker

import com.estagiario.gobots.rinha_backend.domain.PaymentEvent
import com.estagiario.gobots.rinha_backend.domain.PaymentEventStatus
import com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository.PaymentEventRepository
import mu.KotlinLogging
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

private val logger = KotlinLogging.logger {}

@Component
class OutboxRelay(
    private val paymentEventRepository: PaymentEventRepository
) {

    fun claimBatch(ownerId: String, limit: Long): Flux<PaymentEvent> {
        return paymentEventRepository.findByStatusOrderByCreatedAtAsc(PaymentEventStatus.PENDING)
            .take(limit)
            .flatMap { event: PaymentEvent ->
                val updated = event.copy(
                    status = PaymentEventStatus.PROCESSING,
                    owner = ownerId,
                    processingAt = Instant.now()
                )
                paymentEventRepository.save(updated)
            }
    }

    fun markAsProcessed(event: PaymentEvent): Mono<PaymentEvent> {
        val updatedEvent = event.copy(
            status = PaymentEventStatus.PROCESSED,
            processedAt = Instant.now(),
            owner = null
        )
        return paymentEventRepository.save(updatedEvent)
    }

    // ✅ ASSINATURA CORRIGIDA: Recebe 'delaySeconds' como Long
    fun scheduleForRetry(event: PaymentEvent, delaySeconds: Long): Mono<PaymentEvent> {
        val updatedEvent = event.copy(
            status = PaymentEventStatus.PENDING,
            nextRetryAt = Instant.now().plusSeconds(delaySeconds),
            owner = null,
            processingAt = null
        )
        return paymentEventRepository.save(updatedEvent)
    }
}