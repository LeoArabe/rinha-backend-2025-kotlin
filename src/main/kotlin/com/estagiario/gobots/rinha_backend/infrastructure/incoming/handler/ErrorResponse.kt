package com.estagiario.gobots.rinha_backend.infrastructure.incoming.handler

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

/**
 * DTO padrão para respostas de erro da API.
 * Fornece uma estrutura consistente para os clientes da API.
 */
data class ErrorResponse(
    @JsonProperty("timestamp")
    val timestamp: Instant = Instant.now(),

    @JsonProperty("code")
    val code: String,

    @JsonProperty("message")
    val message: String
)