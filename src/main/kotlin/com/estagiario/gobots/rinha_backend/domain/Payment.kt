package com.estagiario.gobots.rinha_backend.domain

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant
import java.math.BigDecimal

@Document("payments")
data class Payment(
    @Id
    val id: String? = null,

    @Indexed(unique = true)  // ⚡ Índice único para performance
    val correlationId: String,

    val amount: Long, // Centavos
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

    fun toBigDecimal(): BigDecimal = BigDecimal.valueOf(amount, 2)

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
}