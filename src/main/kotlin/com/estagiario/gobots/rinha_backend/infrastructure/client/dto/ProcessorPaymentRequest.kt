package com.estagiario.gobots.rinha_backend.infrastructure.client.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal
import java.time.Instant

/**
 * DTO para requisição POST /payments aos processadores.
 */
data class ProcessorPaymentRequest(
    @JsonProperty("correlationId")
    val correlationId: String,

    @JsonProperty("amount")
    val amount: BigDecimal,

    @JsonProperty("requestedAt")
    val requestedAt: Instant
)
