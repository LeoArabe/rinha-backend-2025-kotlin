package com.estagiario.gobots.rinha_backend.infrastructure.incoming.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

/**
 * DTO de resposta para o endpoint GET /payments-summary
 */
data class PaymentSummaryResponse(
    @JsonProperty("default")
    val defaultProcessor: ProcessorSummary,

    @JsonProperty("fallback")
    val fallbackProcessor: ProcessorSummary
) {
    companion object {
        fun empty(): PaymentSummaryResponse = PaymentSummaryResponse(
            defaultProcessor = ProcessorSummary.empty(),
            fallbackProcessor = ProcessorSummary.empty()
        )
    }
}

/**
 * Representa o resumo de um processador para a resposta JSON final.
 */
data class ProcessorSummary(
    @JsonProperty("totalRequests")
    val totalRequests: Long,

    /**
     * ✅ CORRETO: O valor total é BigDecimal para ser serializado como um decimal (ex: 30.00).
     */
    @JsonProperty("totalAmount")
    val totalAmount: BigDecimal
) {
    companion object {
        fun empty(): ProcessorSummary = ProcessorSummary(
            totalRequests = 0L,
            totalAmount = BigDecimal.ZERO
        )
    }
}