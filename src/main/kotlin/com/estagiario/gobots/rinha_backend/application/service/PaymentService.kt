package com.estagiario.gobots.rinha_backend.application.service

import com.estagiario.gobots.rinha_backend.domain.Payment
import com.estagiario.gobots.rinha_backend.infrastructure.incoming.dto.PaymentRequest
import reactor.core.publisher.Mono

data class HealthStatus(
    val isHealthy: Boolean,
    val details: Map<String, Any>
)

interface PaymentService {
    fun processNewPayment(request: PaymentRequest): Mono<Void>
    fun performHealthCheck(): Mono<HealthStatus>
    fun getPaymentStatus(correlationId: String): Mono<Payment>
    fun testPersistence(): Mono<Void>
}