package com.estagiario.gobots.rinha_backend.infrastructure.client.dto

import com.estagiario.gobots.rinha_backend.domain.Payment
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal
import java.time.Instant

/**
 * DTO de requisição para o endpoint POST /payments DOS PROCESSADORES EXTERNOS.
 */
data class ProcessorPaymentRequest(
    @JsonProperty("correlationId")
    val correlationId: String,

    // O tipo BigDecimal está correto, o problema era a formatação.
    @JsonProperty("amount")
    val amount: BigDecimal,

    @JsonProperty("requestedAt")
    val requestedAt: Instant
) {
    companion object {
        fun fromPayment(payment: Payment): ProcessorPaymentRequest {
            // ✅ CORREÇÃO: Forçar a escala de 2 casas decimais aqui
            val formattedAmount = payment.toBigDecimal().setScale(2)

            return ProcessorPaymentRequest(
                correlationId = payment.correlationId,
                amount = formattedAmount,
                requestedAt = payment.requestedAt
            )
        }
    }
}