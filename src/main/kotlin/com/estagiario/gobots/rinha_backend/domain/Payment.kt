package com.estagiario.gobots.rinha_backend.domain

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.math.BigDecimal
import java.time.Instant

@Document("payments")
data class Payment(
    @Id val id: String? = null,
    @Indexed(unique = true) val correlationId: String,
    val amount: Long, // Em centavos
    val status: PaymentStatus,
    val requestedAt: Instant,
    val lastUpdatedAt: Instant,
    val processorUsed: String? = null,
    val lastErrorMessage: String? = null
) {
    companion object {
        fun newPending(correlationId: String, amountCents: Long): Payment {
            val now = Instant.now()
            return Payment(
                correlationId = correlationId,
                amount = amountCents,
                status = PaymentStatus.PROCESSING,
                requestedAt = now,
                lastUpdatedAt = now
            )
        }
    }

    fun markAsSuccessful(processor: String): Payment {
        return this.copy(
            status = PaymentStatus.SUCCESS,
            processorUsed = processor,
            lastUpdatedAt = Instant.now(),
            lastErrorMessage = null
        )
    }

    fun markAsFailed(error: String?): Payment {
        return this.copy(
            status = PaymentStatus.FAILURE,
            lastErrorMessage = error,
            processorUsed = "none",
            lastUpdatedAt = Instant.now()
        )
    }

    fun toBigDecimal(): BigDecimal = BigDecimal.valueOf(amount, 2)
}