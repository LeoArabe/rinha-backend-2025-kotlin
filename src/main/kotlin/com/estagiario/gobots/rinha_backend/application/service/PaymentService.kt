package com.estagiario.gobots.rinha_backend.application.service

import com.estagiario.gobots.rinha_backend.infrastructure.incoming.dto.PaymentRequest

interface PaymentService {
    suspend fun processNewPayment(request: PaymentRequest)
}