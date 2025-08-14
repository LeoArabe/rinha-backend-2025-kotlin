package com.estagiario.gobots.rinha_backend.infrastructure.config

import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration

@Configuration
class WebClientConfig {

    private fun buildClient(baseUrl: String, timeout: Long): WebClient {
        val httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2_000)
            .responseTimeout(Duration.ofSeconds(timeout))
            .doOnConnected {
                it.addHandlerLast(ReadTimeoutHandler(timeout.toInt()))
                it.addHandlerLast(WriteTimeoutHandler(timeout.toInt()))
            }
        return WebClient.builder()
            .baseUrl(baseUrl)
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .build()
    }

    @Bean("defaultProcessorWebClient")
    fun defaultProcessorWebClient(
        @Value("\${app.processor.default.url}") baseUrl: String,
        @Value("\${payment.circuit-breaker.request-timeout-seconds:4}") timeout: Long
    ): WebClient = buildClient(baseUrl, timeout)

    @Bean("fallbackProcessorWebClient")
    fun fallbackProcessorWebClient(
        @Value("\${app.processor.fallback.url}") baseUrl: String,
        @Value("\${payment.circuit-breaker.request-timeout-seconds:4}") timeout: Long
    ): WebClient = buildClient(baseUrl, timeout)
}
