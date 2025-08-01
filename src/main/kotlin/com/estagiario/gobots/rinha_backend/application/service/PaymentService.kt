package com.estagiario.gobots.rinha_backend.application.service

import com.estagiario.gobots.rinha_backend.infrastructure.incoming.dto.PaymentRequest
import reactor.core.publisher.Mono

interface PaymentService {
    // Retorna Mono<Void> para se integrar com o contexto reativo
    fun processNewPayment(request: PaymentRequest): Mono<Void>
}