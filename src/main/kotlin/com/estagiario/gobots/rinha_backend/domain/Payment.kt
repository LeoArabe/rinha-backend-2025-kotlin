package com.estagiario.gobots.rinha_backend.domain

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.math.BigDecimal
import java.time.Instant

@Document("payments")
data class Payment(
    @Id
    val id: String? = null,

    @Indexed(unique = true)
    val correlationId: String,

    val amount: Long, // em centavos
    val status: PaymentStatus,
    val requestedAt: Instant,
    val lastUpdatedAt: Instant,

    val processorUsed: String? = null,
    val lastErrorMessage: String? = null,
    val attemptCount: Int = 0,
    val nextRetryAt: Instant? = null
) {
    companion object {
        fun newPayment(correlationId: String, amountInCents: Long): Payment {
            val now = Instant.now()
            return Payment(
                correlationId = correlationId,
                amount = amountInCents,
                status = PaymentStatus.RECEIVED,
                requestedAt = now,
                lastUpdatedAt = now
            )
        }
    }

    val createdAt: Instant get() = requestedAt

    fun toBigDecimal(): BigDecimal =
        BigDecimal.valueOf(amount, 2)

    fun markAsProcessing(processor: String): Payment =
        this.copy(
            status = PaymentStatus.PROCESSING,
            processorUsed = processor,
            lastUpdatedAt = Instant.now()
        )

    fun markAsFailed(message: String?): Payment =
        this.copy(
            status = PaymentStatus.FAILURE,
            lastErrorMessage = message,
            lastUpdatedAt = Instant.now()
        )

    fun markAsSuccess(processor: String): Payment =
        this.copy(
            status = PaymentStatus.SUCCESS,
            processorUsed = processor,
            lastUpdatedAt = Instant.now()
        )

    fun incrementAttempts(nextRetry: Instant?): Payment =
        this.copy(
            attemptCount = attemptCount + 1,
            nextRetryAt = nextRetry,
            lastUpdatedAt = Instant.now()
        )
}
