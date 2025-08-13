package com.estagiario.gobots.rinha_backend.domain

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document("payments")
data class Payment(
    @Id val id: String? = null,
    @Indexed(unique = true) val correlationId: String,
    val amount: Long,
    val status: PaymentStatus,
    val requestedAt: Instant,
    val lastUpdatedAt: Instant,
    val processorUsed: String? = null,
    val lastErrorMessage: String? = null,
    val attemptCount: Int = 0,
    val nextRetryAt: Instant? = null
) {
    companion object {
        fun newPayment(correlationId: String, amount: Long): Payment {
            val now = Instant.now()
            return Payment(
                correlationId = correlationId,
                amount = amount,
                status = PaymentStatus.RECEBIDO,
                requestedAt = now,
                lastUpdatedAt = now
            )
        }

        // ✅ GARANTA QUE ESTE MÉTODO ESTÁ AQUI DENTRO
        fun newPending(correlationId: String, amountCents: Long): Payment {
            val now = Instant.now()
            return Payment(
                correlationId = correlationId,
                amount = amountCents,
                status = PaymentStatus.PROCESSANDO,
                requestedAt = now,
                lastUpdatedAt = now
            )
        }
    }

    val createdAt: Instant get() = requestedAt

    fun toBigDecimal(): java.math.BigDecimal {
        return java.math.BigDecimal(amount).divide(java.math.BigDecimal(100))
    }
}