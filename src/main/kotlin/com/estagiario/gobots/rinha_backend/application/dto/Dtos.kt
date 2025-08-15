package com.estagiario.gobots.rinha_backend.application.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

// --- Sumário ---
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

// --- Health Check ---
data class ProcessorHealthResponse(
    @JsonProperty("failing")
    val failing: Boolean?,
    @JsonProperty("minResponseTime")
    val minResponseTime: Int
)