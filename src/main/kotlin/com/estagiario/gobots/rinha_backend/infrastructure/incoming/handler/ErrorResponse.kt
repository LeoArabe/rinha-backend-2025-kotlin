package com.estagiario.gobots.rinha_backend.infrastructure.incoming.handler

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

data class ErrorResponse(
    @JsonProperty("timestamp")
    val timestamp: Instant = Instant.now(),

    @JsonProperty("code")
    val code: String,

    @JsonProperty("message")
    val message: String
)
