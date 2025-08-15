package com.estagiario.gobots.rinha_backend.infrastructure.client.impl

import com.estagiario.gobots.rinha_backend.application.dto.ProcessorHealthResponse
import com.estagiario.gobots.rinha_backend.application.ports.PaymentProcessorClient
import com.estagiario.gobots.rinha_backend.application.ports.ProcessorHealthClient
import com.estagiario.gobots.rinha_backend.infrastructure.client.dto.ProcessorPaymentRequest
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

@Component
@Qualifier("defaultClient")
class DefaultProcessorClient(
    @Qualifier("defaultProcessorWebClient") private val webClient: WebClient
) : PaymentProcessorClient {

    override fun process(correlationId: String, amount: BigDecimal, requestedAt: Instant): Mono<Void> {
        val request = ProcessorPaymentRequest(correlationId, amount, requestedAt)
        return webClient.post()
            .uri("/payments")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(Void::class.java)
            .timeout(Duration.ofSeconds(4))
    }
}

@Component
@Qualifier("fallbackClient")
class FallbackProcessorClient(
    @Qualifier("fallbackProcessorWebClient") private val webClient: WebClient
) : PaymentProcessorClient {

    override fun process(correlationId: String, amount: BigDecimal, requestedAt: Instant): Mono<Void> {
        val request = ProcessorPaymentRequest(correlationId, amount, requestedAt)
        return webClient.post()
            .uri("/payments")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(Void::class.java)
            .timeout(Duration.ofSeconds(4))
    }
}

@Component
@Qualifier("defaultHealthClient")
class DefaultProcessorHealthClient(
    @Qualifier("defaultProcessorWebClient") private val webClient: WebClient
) : ProcessorHealthClient {
    override fun checkHealth(): Mono<ProcessorHealthResponse> {
        return webClient.get()
            .uri("/payments/service-health")
            .retrieve()
            .bodyToMono(ProcessorHealthResponse::class.java)
    }
}

@Component
@Qualifier("fallbackHealthClient")
class FallbackProcessorHealthClient(
    @Qualifier("fallbackProcessorWebClient") private val webClient: WebClient
) : ProcessorHealthClient {
    override fun checkHealth(): Mono<ProcessorHealthResponse> {
        return webClient.get()
            .uri("/payments/service-health")
            .retrieve()
            .bodyToMono(ProcessorHealthResponse::class.java)
    }
}