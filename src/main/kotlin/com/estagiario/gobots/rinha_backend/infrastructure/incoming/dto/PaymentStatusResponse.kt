package com.estagiario.gobots.rinha_backend.infrastructure.incoming.dto

import java.time.Instant

/**
 * DTO for the GET /payments/{correlationId}/status endpoint response.
 */
data class PaymentStatusResponse(
    val correlationId: String,
    val status: String,
    val amount: Long,
    val createdAt: Instant,
    val processor: String?,
    val errorMessage: String?
)