package com.estagiario.gobots.rinha_backend.application.client

import com.estagiario.gobots.rinha_backend.infrastructure.client.dto.ProcessorHealthResponse
import com.estagiario.gobots.rinha_backend.infrastructure.client.dto.ProcessorPaymentRequest
import com.estagiario.gobots.rinha_backend.infrastructure.client.dto.ProcessorPaymentResponse
import reactor.core.publisher.Mono

interface ProcessorClient {
    fun processPaymentDefault(request: ProcessorPaymentRequest): Mono<ProcessorPaymentResponse>
    fun processPaymentFallback(request: ProcessorPaymentRequest): Mono<ProcessorPaymentResponse>
    fun checkDefaultProcessorHealth(): Mono<ProcessorHealthResponse>
    fun checkFallbackProcessorHealth(): Mono<ProcessorHealthResponse>
}