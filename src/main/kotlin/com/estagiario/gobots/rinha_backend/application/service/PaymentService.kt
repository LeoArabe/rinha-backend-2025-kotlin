package com.estagiario.gobots.rinha_backend.application.service

import com.estagiario.gobots.rinha_backend.domain.Payment
import com.estagiario.gobots.rinha_backend.domain.PaymentStatus
import com.estagiario.gobots.rinha_backend.infrastructure.incoming.dto.PaymentRequest
import reactor.core.publisher.Mono

interface PaymentService {
    fun processNewPayment(request: PaymentRequest): Mono<Payment>
    fun getPaymentStatus(correlationId: String): Mono<Payment>
    fun updatePaymentStatus(payment: Payment, newStatus: PaymentStatus, processor: String?, error: String? = null): Mono<Payment>
}