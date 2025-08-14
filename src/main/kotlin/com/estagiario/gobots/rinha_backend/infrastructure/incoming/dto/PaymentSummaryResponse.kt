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
        /**
         * Factory method to safely create a response from raw MongoDB aggregation results.
         * It encapsulates the unsafe casting and data mapping logic.
         */
        @Suppress("UNCHECKED_CAST")
        fun fromAggregation(results: List<Map<*, *>>): PaymentSummaryResponse {
            val resultsMap = results.associateBy { it["_id"] as? String }

            val defaultData = resultsMap["default"] as? Map<String, Any>
            val fallbackData = resultsMap["fallback"] as? Map<String, Any>

            return PaymentSummaryResponse(
                defaultProcessor = mapToProcessorSummary(defaultData),
                fallbackProcessor = mapToProcessorSummary(fallbackData)
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
        fun empty(): ProcessorSummary = ProcessorSummary(
            totalRequests = 0L,
            totalAmount = BigDecimal.ZERO
        )
    }
}