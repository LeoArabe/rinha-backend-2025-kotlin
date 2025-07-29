package com.estagiario.gobots.rinha_backend.application.worker

import com.estagiario.gobots.rinha_backend.domain.Payment
import com.estagiario.gobots.rinha_backend.domain.PaymentEvent

interface PaymentProcessorWorker {
    suspend fun processPaymentFromQueue(event: PaymentEvent, payment: Payment)
}