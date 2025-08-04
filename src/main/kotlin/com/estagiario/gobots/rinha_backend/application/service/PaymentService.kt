package com.estagiario.gobots.rinha_backend.application.service

import com.estagiario.gobots.rinha_backend.infrastructure.incoming.dto.PaymentRequest
// ATUALIZE: src/main/kotlin/com/estagiario/gobots/rinha_backend/application/service/PaymentService.kt
import reactor.core.publisher.Mono

interface PaymentService {
    fun processNewPayment(request: PaymentRequest): Mono<Void>
}