package com.estagiario.gobots.rinha_backend.application.ports

import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

interface PaymentProcessorClient {
    fun process(
        correlationId: UUID,
        amount: BigDecimal,
        requestedAt: Instant
    ): Mono<Void>
}
