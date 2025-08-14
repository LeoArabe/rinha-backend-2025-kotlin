package com.estagiario.gobots.rinha_backend.application.service

import com.estagiario.gobots.rinha_backend.application.dto.HealthCheckResponse
import com.estagiario.gobots.rinha_backend.application.dto.PaymentAck
import com.estagiario.gobots.rinha_backend.application.dto.PaymentRequest
import com.estagiario.gobots.rinha_backend.domain.Payment
import reactor.core.publisher.Mono

interface PaymentService {
    fun processNewPayment(request: PaymentRequest): Mono<PaymentAck>
    fun performHealthCheck(): Mono<HealthCheckResponse>
    fun testPersistence(): Mono<Void>
    fun getPaymentStatus(correlationId: String): Mono<Payment>
}
