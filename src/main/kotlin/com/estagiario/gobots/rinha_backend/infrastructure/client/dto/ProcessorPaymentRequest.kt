package com.estagiario.gobots.rinha_backend.infrastructure.client.dto

import java.math.BigDecimal
import java.time.Instant

data class ProcessorPaymentRequest(
    val correlationId: String,
    val amount: BigDecimal,
    val requestedAt: Instant
)
