package com.estagiario.gobots.rinha_backend.application.dto

import java.math.BigDecimal
import java.util.UUID

data class PaymentRequest(
    val correlationId: UUID,
    val amount: BigDecimal
)

data class PaymentAck(
    val correlationId: UUID
)
