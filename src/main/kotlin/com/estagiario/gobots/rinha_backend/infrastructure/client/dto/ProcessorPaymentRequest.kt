package com.estagiario.gobots.rinha_backend.infrastructure.client.dto

import com.estagiario.gobots.rinha_backend.domain.Payment
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

/**
 * DTO de requisição para o endpoint POST /payments DOS PROCESSADORES EXTERNOS.
 */
data class ProcessorPaymentRequest(
    @JsonProperty("correlationId")
    val correlationId: String,

    @JsonProperty("amount")
    val amount: Long,

    @JsonProperty("requestedAt")
    val requestedAt: Instant
) {
    companion object {
        fun fromPayment(payment: Payment): ProcessorPaymentRequest {
            return ProcessorPaymentRequest(
                correlationId = payment.correlationId,
                amount = payment.amount,
                requestedAt = payment.requestedAt
            )
        }
    }
}