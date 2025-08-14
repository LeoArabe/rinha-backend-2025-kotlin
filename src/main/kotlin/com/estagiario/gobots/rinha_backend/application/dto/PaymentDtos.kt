package com.estagiario.gobots.rinha_backend.application.dto

import java.math.BigDecimal
import java.util.UUID

data class PaymentRequest(
    val correlationId: UUID,
    val amount: BigDecimal
)

data class PaymentAck(
    val correlationId: UUID
)

data class PaymentsSummary(
    val default: SummaryPart,
    val fallback: SummaryPart
)

data class SummaryPart(
    val totalRequests: Long,
    val totalAmount: BigDecimal
) {
    companion object {
        fun empty() = SummaryPart(0L, BigDecimal.ZERO)
    }
}

data class HealthCheckResponse(
    val isHealthy: Boolean,
    val details: Map<String, Any>
)
