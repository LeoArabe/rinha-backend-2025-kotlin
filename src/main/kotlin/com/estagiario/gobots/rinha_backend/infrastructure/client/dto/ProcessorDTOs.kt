package com.estagiario.gobots.rinha_backend.infrastructure.client.dto

import com.estagiario.gobots.rinha_backend.domain.Payment
import java.math.BigDecimal
import java.time.Instant

/**
 * Request para processar pagamento no processador externo
 */
data class ProcessorPaymentRequest(
    val correlationId: String,
    val amount: BigDecimal,
    val requestedAt: Instant
) {
    companion object {
        fun fromPayment(payment: Payment): ProcessorPaymentRequest {
            return ProcessorPaymentRequest(
                correlationId = payment.correlationId,
                amount = payment.toBigDecimal(),
                requestedAt = payment.requestedAt
            )
        }
    }
}

/**
 * Response do processamento de pagamento
 */
data class ProcessorPaymentResponse(
    val correlationId: String,
    val status: String,
    val processedAt: Instant,
    val message: String? = null
)

/**
 * Response do health check do processador
 */
data class ProcessorHealthResponse(
    val status: String,
    val timestamp: Instant,
    val details: Map<String, Any>? = null
)