package com.estagiario.gobots.rinha_backend.domain

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document("payment_events")
data class PaymentEvent(
    @Id
    val id: String? = null,
    val paymentId: String,
    val correlationId: String,
    val status: PaymentEventStatus,
    val createdAt: Instant,
    val processingAt: Instant? = null,
    val processedAt: Instant? = null,
    val owner: String? = null,
    val nextRetryAt: Instant? = null
) {
    companion object {
        fun newEvent(payment: Payment): PaymentEvent {
            val now = Instant.now()
            return PaymentEvent(
                paymentId = payment.id ?: error("Payment ID cannot be null"),
                correlationId = payment.correlationId,
                status = PaymentEventStatus.PENDING,
                createdAt = now
            )
        }
    }

    fun claimForProcessing(ownerId: String): PaymentEvent =
        this.copy(
            status = PaymentEventStatus.PROCESSING,
            owner = ownerId,
            processingAt = Instant.now()
        )

    fun markAsProcessed(): PaymentEvent =
        this.copy(
            status = PaymentEventStatus.PROCESSED,
            processedAt = Instant.now()
        )

    fun markAsFailed(nextRetry: Instant?): PaymentEvent =
        this.copy(
            status = PaymentEventStatus.FAILED,
            nextRetryAt = nextRetry
        )
}
