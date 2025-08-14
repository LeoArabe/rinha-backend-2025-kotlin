package com.estagiario.gobots.rinha_backend.infrastructure.client

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

@Component
@Qualifier("fallbackClient")
class FallbackProcessorClient(
    @Qualifier("fallbackProcessorWebClient") private val webClient: WebClient
) : PaymentProcessorClient {

    override fun process(
        correlationId: UUID,
        amount: BigDecimal,
        requestedAt: Instant
    ): Mono<Void> {
        val request = ProcessorPaymentRequest(
            correlationId = correlationId.toString(),
            amount = amount,
            requestedAt = requestedAt
        )

        return webClient.post()
            .uri("/payments")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(Void::class.java)
            .timeout(Duration.ofSeconds(4))
    }
}
