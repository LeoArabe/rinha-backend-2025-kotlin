package com.estagiario.gobots.rinha_backend.domain

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

/**
 * Entidade principal Payment - A fonte única da verdade para cada transação.
 */
@Document("payments")
// ✅ Seu índice composto está perfeito para a query do summary!
@CompoundIndex(name = "summary_idx", def = "{'status': 1, 'processorUsed': 1, 'lastUpdatedAt': 1}")
data class Payment(
    @Id
    val correlationId: String,

    val amount: Long, // Valor em centavos

    val requestedAt: Instant,

    // ✅ CORREÇÃO: Campos que mudam durante o ciclo de vida devem ser 'var'
    var status: PaymentStatus,
    var lastUpdatedAt: Instant,
    var processorUsed: String? = null,
    var attemptCount: Int = 0,
    var nextRetryAt: Instant? = null,
    var lastErrorMessage: String? = null,

    // ✅ ADIÇÃO ESTRATÉGICA: Campos para robustez e auditoria
    var processorFee: Long? = null,
    var externalTransactionId: String? = null
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
    }
}