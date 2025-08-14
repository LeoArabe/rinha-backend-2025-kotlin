package com.estagiario.gobots.rinha_backend.infrastructure.incoming.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

data class PaymentSummaryResponse(
    @JsonProperty("default")
    val defaultProcessor: ProcessorSummary,

    @JsonProperty("fallback")
    val fallbackProcessor: ProcessorSummary
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromAggregation(results: List<Map<*, *>>): PaymentSummaryResponse {
            val resultsMap = results.associateBy { it["_id"] as? String }
            return PaymentSummaryResponse(
                defaultProcessor = mapToProcessorSummary(resultsMap["default"] as? Map<String, Any>),
                fallbackProcessor = mapToProcessorSummary(resultsMap["fallback"] as? Map<String, Any>)
            )
        }

        private fun mapToProcessorSummary(data: Map<String, Any>?): ProcessorSummary {
            if (data == null) return ProcessorSummary.empty()
            val totalAmountCents = (data["totalAmount"] as? Number)?.toLong() ?: 0L
            return ProcessorSummary(
                totalRequests = (data["totalRequests"] as? Number)?.toLong() ?: 0L,
                totalAmount = BigDecimal(totalAmountCents).movePointLeft(2)
            )
        }
    }
}

data class ProcessorSummary(
    val totalRequests: Long,
    val totalAmount: BigDecimal
) {
    companion object {
        fun empty() = ProcessorSummary(0L, BigDecimal.ZERO)
    }
}
