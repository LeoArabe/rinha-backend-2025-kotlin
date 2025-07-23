package com.estagiario.gobots.rinha_backend.application.client

import com.estagiario.gobots.rinha_backend.infrastructure.client.dto.ProcessorHealthResponse
import com.estagiario.gobots.rinha_backend.infrastructure.client.dto.ProcessorPaymentRequest
import com.estagiario.gobots.rinha_backend.infrastructure.client.dto.ProcessorPaymentResponse

interface ProcessorClient {
    suspend fun checkDefaultProcessorHealth(): ProcessorHealthResponse
    suspend fun checkFallbackProcessorHealth(): ProcessorHealthResponse
    suspend fun processPaymentDefault(request: ProcessorPaymentRequest): ProcessorPaymentResponse
    suspend fun processPaymentFallback(request: ProcessorPaymentRequest): ProcessorPaymentResponse
}