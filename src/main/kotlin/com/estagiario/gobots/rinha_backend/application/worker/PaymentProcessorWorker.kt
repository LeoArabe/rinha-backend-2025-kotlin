package com.estagiario.gobots.rinha_backend.application.worker

import com.estagiario.gobots.rinha_backend.domain.Payment
import com.estagiario.gobots.rinha_backend.domain.PaymentEvent
import reactor.core.publisher.Mono

interface PaymentProcessorWorker {
    fun processPaymentFromQueue(event: PaymentEvent, payment: Payment): Mono<Void>
}