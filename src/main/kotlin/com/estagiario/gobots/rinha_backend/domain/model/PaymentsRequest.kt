package com.estagiario.gobots.rinha_backend.domain.model

import java.util.UUID

data class PaymentsRequest(
    val correlationId: UUID,
    val amount: Long
)