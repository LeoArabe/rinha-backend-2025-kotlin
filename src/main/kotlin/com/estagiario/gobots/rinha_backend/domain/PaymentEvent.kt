package com.estagiario.gobots.rinha_backend.domain

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document("payment_events")
data class PaymentEvent(
    @Id val id: String? = null,
    val correlationId: String,
    val status: PaymentEventStatus, // ✅ USA O NOVO ENUM
    val createdAt: Instant,
    val owner: String? = null,
    val processingAt: Instant? = null,
    val processedAt: Instant? = null,
    val nextRetryAt: Instant? = null,
    val attemptCount: Int = 0,
    val lastErrorMessage: String? = null
) {
    companion object {
        fun newProcessPaymentEvent(correlationId: String): PaymentEvent {
            return PaymentEvent(
                correlationId = correlationId,
                status = PaymentEventStatus.PENDING, // ✅ USA O NOVO ENUM
                createdAt = Instant.now(),
                nextRetryAt = Instant.now()
            )
        }
    }

    fun markAsProcessing(ownerId: String): PaymentEvent {
        return this.copy(status = PaymentEventStatus.PROCESSING, owner = ownerId, processingAt = Instant.now())
    }

    fun markAsProcessed(): PaymentEvent {
        return this.copy(status = PaymentEventStatus.PROCESSED, processedAt = Instant.now(), owner = null)
    }

    fun scheduleForRetry(delaySeconds: Long, error: String?): PaymentEvent {
        return this.copy(
            status = PaymentEventStatus.PENDING, // Volta para PENDING para ser pego novamente
            nextRetryAt = Instant.now().plusSeconds(delaySeconds),
            owner = null,
            processingAt = null,
            attemptCount = this.attemptCount + 1,
            lastErrorMessage = error
        )
    }
}