package com.estagiario.gobots.rinha_backend.application.ports

import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.Instant

interface PaymentProcessorClient {
    // Corrigido para usar String, que é o tipo no domínio
    fun process(correlationId: String, amount: BigDecimal, requestedAt: Instant): Mono<Void>
}