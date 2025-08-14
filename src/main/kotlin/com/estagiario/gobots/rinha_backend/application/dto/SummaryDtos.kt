package com.estagiario.gobots.rinha_backend.application.dto

import java.math.BigDecimal

data class SummaryPart(
    val totalRequests: Long,
    val totalAmount: BigDecimal
)

data class PaymentsSummary(
    val default: SummaryPart,
    val fallback: SummaryPart
)
