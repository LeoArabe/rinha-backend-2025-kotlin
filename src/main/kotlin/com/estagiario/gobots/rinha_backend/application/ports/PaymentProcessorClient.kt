package com.estagiario.gobots.rinha_backend.application.ports

import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.Instant
import java.util.*

/**
 * Porta para chamar um Payment Processor (default/fallback).
 * A infra fornece 2 beans: @Qualifier("defaultClient") e @Qualifier("fallbackClient").
 */
interface PaymentProcessorClient {
    fun process(correlationId: UUID, amount: BigDecimal, requestedAt: Instant): Mono<Void>
}
