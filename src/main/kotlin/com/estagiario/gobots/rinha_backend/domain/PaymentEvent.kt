package com.estagiario.gobots.rinha_backend.domain

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

/**
 * Outbox event / payment processing lifecycle.
 * Coleção: payment_events
 */
@Document("payment_events")
data class PaymentEvent(
    @Id
    val id: String? = null,
    val correlationId: String,
    val status: PaymentEventStatus,
    val createdAt: Instant,
    val owner: String? = null,
    val processingAt: Instant? = null,
    val processedAt: Instant? = null,
    val nextRetryAt: Instant? = null
) {
    companion object {
        fun newProcessPaymentEvent(correlationId: String): PaymentEvent {
            return PaymentEvent(
                correlationId = correlationId,
                status = PaymentEventStatus.PENDING,
                createdAt = Instant.now()
            )
        }
    }

    /**
     * Marca para processamento por um owner (não persiste; retorna cópia)
     */
    fun claimForProcessing(ownerId: String): PaymentEvent =
        this.copy(status = PaymentEventStatus.PROCESSING, owner = ownerId, processingAt = Instant.now())

    /**
     * Marca como processado (retira owner)
     */
    fun markProcessed(): PaymentEvent =
        this.copy(status = PaymentEventStatus.PROCESSED, processedAt = Instant.now(), owner = null)
}
