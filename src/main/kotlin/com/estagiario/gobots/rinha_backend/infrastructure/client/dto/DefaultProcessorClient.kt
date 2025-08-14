package com.estagiario.gobots.rinha_backend.infrastructure.client.impl

import com.estagiario.gobots.rinha_backend.application.ports.PaymentProcessorClient
import com.estagiario.gobots.rinha_backend.infrastructure.client.dto.ProcessorPaymentRequest
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.*

/**
 * Cliente HTTP para processadores de pagamento.
 */
@Component
@Qualifier("defaultClient")
class DefaultProcessorClient(
    @Qualifier("defaultProcessorWebClient") private val webClient: WebClient
) : PaymentProcessorClient {

    override fun process(correlationId: UUID, amount: BigDecimal, requestedAt: Instant): Mono<Void> {
        val request = ProcessorPaymentRequest(correlationId.toString(), amount, requestedAt)
        return webClient.post()
            .uri("/payments")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(String::class.java)
            .then()
            .timeout(Duration.ofSeconds(4))
    }
}

@Component
@Qualifier("fallbackClient")
class FallbackProcessorClient(
    @Qualifier("fallbackProcessorWebClient") private val webClient: WebClient
) : PaymentProcessorClient {

    override fun process(correlationId: UUID, amount: BigDecimal, requestedAt: Instant): Mono<Void> {
        val request = ProcessorPaymentRequest(correlationId.toString(), amount, requestedAt)
        return webClient.post()
            .uri("/payments")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(String::class.java)
            .then()
            .timeout(Duration.ofSeconds(4))
    }
}
