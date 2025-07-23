package com.estagiario.gobots.rinha_backend.infrastructure.client.dto

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * DTO de resposta para o endpoint POST /payments dos processadores.
 */
data class ProcessorPaymentResponse(
    @JsonProperty("message")
    val message: String
)