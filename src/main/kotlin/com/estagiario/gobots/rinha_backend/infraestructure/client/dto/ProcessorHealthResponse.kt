package com.estagiario.gobots.rinha_backend.infrastructure.client.dto

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * DTO de resposta para o endpoint GET /payments/service-health dos processadores.
 */
data class ProcessorHealthResponse(
    @JsonProperty("failing")
    val failing: Boolean,

    @JsonProperty("minResponseTime")
    val minResponseTime: Int
)