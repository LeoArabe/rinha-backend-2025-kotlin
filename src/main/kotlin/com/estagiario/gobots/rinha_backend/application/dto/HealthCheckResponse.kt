package com.estagiario.gobots.rinha_backend.application.dto

data class HealthCheckResponse(
    val isHealthy: Boolean,
    val details: Map<String, Any>
)