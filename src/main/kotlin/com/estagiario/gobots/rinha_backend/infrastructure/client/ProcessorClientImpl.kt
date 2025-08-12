package com.estagiario.gobots.rinha_backend.infrastructure.client

import com.estagiario.gobots.rinha_backend.application.client.ProcessorClient
import com.estagiario.gobots.rinha_backend.infrastructure.client.dto.ProcessorHealthResponse
import com.estagiario.gobots.rinha_backend.infrastructure.client.dto.ProcessorPaymentRequest
import com.estagiario.gobots.rinha_backend.infrastructure.client.dto.ProcessorPaymentResponse
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.time.Duration

@Component
class ProcessorClientImpl(
    @Qualifier("defaultProcessorWebClient") private val defaultWebClient: WebClient,
    @Qualifier("fallbackProcessorWebClient") private val fallbackWebClient: WebClient,
    @Value("\${payment.circuit-breaker.request-timeout-seconds:4}") private val requestTimeoutSeconds: Long
) : ProcessorClient {

    override fun processPaymentDefault(request: ProcessorPaymentRequest): Mono<ProcessorPaymentResponse> {
        return processPayment(defaultWebClient, request)
    }

    override fun processPaymentFallback(request: ProcessorPaymentRequest): Mono<ProcessorPaymentResponse> {
        return processPayment(fallbackWebClient, request)
    }

    override fun checkDefaultProcessorHealth(): Mono<ProcessorHealthResponse> {
        return checkProcessorHealth(defaultWebClient)
    }

    override fun checkFallbackProcessorHealth(): Mono<ProcessorHealthResponse> {
        return checkProcessorHealth(fallbackWebClient)
    }

    private fun processPayment(webClient: WebClient, request: ProcessorPaymentRequest): Mono<ProcessorPaymentResponse> {
        return webClient.post()
            .uri("/payments")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(ProcessorPaymentResponse::class.java)
            .timeout(Duration.ofSeconds(requestTimeoutSeconds))
    }

    private fun checkProcessorHealth(webClient: WebClient): Mono<ProcessorHealthResponse> {
        return webClient.get()
            .uri("/payments/service-health")
            .retrieve()
            .bodyToMono(ProcessorHealthResponse::class.java)
            .timeout(Duration.ofSeconds(requestTimeoutSeconds))
    }
}
