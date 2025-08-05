package com.estagiario.gobots.rinha_backend.infrastructure.client.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.estagiario.gobots.rinha_backend.domain.Payment
import java.math.BigDecimal
import java.time.Instant

/**
 * DTO de requisição para o endpoint POST /payments DOS PROCESSADORES EXTERNOS.
 */
data class ProcessorPaymentRequest(
    @JsonProperty("correlationId")
    val correlationId: String,

    @JsonProperty("amount")
    val amount: BigDecimal,

    @JsonProperty("requestedAt")
    val requestedAt: Instant = Instant.now()
) {
    companion object {
        fun fromPayment(payment: Payment): ProcessorPaymentRequest {
            return ProcessorPaymentRequest(
                correlationId = payment.correlationId,
                amount = payment.amount
            )
        }
    }
}