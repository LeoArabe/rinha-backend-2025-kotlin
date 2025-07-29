package com.estagiario.gobots.rinha_backend.domain

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.mapping.Document
import java.math.BigDecimal
import java.time.Instant

/**
 * Entidade principal Payment - A fonte única da verdade para cada transação.
 */
@Document("payments")
@CompoundIndex(name = "summary_idx", def = "{'status': 1, 'processorUsed': 1, 'lastUpdatedAt': 1}")
data class Payment(
    @Id
    val correlationId: String,

    val amount: BigDecimal,

    val status: PaymentStatus,

    val requestedAt: Instant,

    var lastUpdatedAt: Instant,

    var processorUsed: String? = null,

    var processorFee: BigDecimal? = null,

    var attemptCount: Int = 0,

    var nextRetryAt: Instant? = null,

    var lastErrorMessage: String? = null,

    var externalTransactionId: String? = null
) {
    companion object {
        fun newPayment(correlationId: String, amount: BigDecimal): Payment {
            val now = Instant.now()
            return Payment(
                correlationId = correlationId,
                amount = amount,
                status = PaymentStatus.RECEBIDO,
                requestedAt = now,
                lastUpdatedAt = now
            )
        }
    }
}