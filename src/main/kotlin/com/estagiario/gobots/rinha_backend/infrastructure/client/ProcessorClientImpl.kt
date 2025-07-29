package com.estagiario.gobots.rinha_backend.infrastructure.client

import com.estagiario.gobots.rinha_backend.application.client.ProcessorClient
import com.estagiario.gobots.rinha_backend.infrastructure.client.dto.ProcessorHealthResponse
import com.estagiario.gobots.rinha_backend.infrastructure.client.dto.ProcessorPaymentRequest
import com.estagiario.gobots.rinha_backend.infrastructure.client.dto.ProcessorPaymentResponse
import io.netty.channel.ChannelOption
import kotlinx.coroutines.reactor.awaitSingle
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.netty.http.client.HttpClient
import java.time.Duration

@Component
class ProcessorClientImpl(
    private val webClientBuilder: WebClient.Builder,
    @Value("\${payment.processors.default.url}") private val defaultProcessorUrl: String,
    @Value("\${payment.processors.fallback.url}") private val fallbackProcessorUrl: String,
    @Value("\${payment.circuit-breaker.request-timeout-seconds}") private val requestTimeoutSeconds: Long
) : ProcessorClient {

    private val logger = KotlinLogging.logger {}

    private val defaultWebClient: WebClient by lazy { createWebClientForProcessor(defaultProcessorUrl) }
    private val fallbackWebClient: WebClient by lazy { createWebClientForProcessor(fallbackProcessorUrl) }

    private fun createWebClientForProcessor(baseUrl: String): WebClient {
        val httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2000) // 2s connection timeout

        return webClientBuilder.clone()
            .baseUrl(baseUrl)
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .build()
    }

    override suspend fun checkDefaultProcessorHealth(): ProcessorHealthResponse =
        checkHealth(defaultWebClient, "default")

    override suspend fun checkFallbackProcessorHealth(): ProcessorHealthResponse =
        checkHealth(fallbackWebClient, "fallback")

    override suspend fun processPaymentDefault(request: ProcessorPaymentRequest): ProcessorPaymentResponse =
        processPayment(defaultWebClient, "default", request)

    override suspend fun processPaymentFallback(request: ProcessorPaymentRequest): ProcessorPaymentResponse =
        processPayment(fallbackWebClient, "fallback", request)

    private suspend fun checkHealth(client: WebClient, processorName: String): ProcessorHealthResponse {
        return try {
            client.get().uri("/payments/service-health")
                .retrieve()
                .bodyToMono(ProcessorHealthResponse::class.java)
                .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                .awaitSingle()
        } catch (e: Exception) {
            logger.warn { "Health check for $processorName failed: ${e.message}" }
            throw e
        }
    }

    private suspend fun processPayment(client: WebClient, processorName: String, request: ProcessorPaymentRequest): ProcessorPaymentResponse {
        return try {
            client.post().uri("/payments")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(ProcessorPaymentResponse::class.java)
                .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                .awaitSingle()
        } catch (e: Exception) {
            logger.warn { "Payment processing via $processorName for ${request.correlationId} failed: ${e.message}" }
            throw e
        }
    }
}