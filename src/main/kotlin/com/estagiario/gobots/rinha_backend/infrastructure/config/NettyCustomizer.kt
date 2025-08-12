package com.estagiario.gobots.rinha_backend.infrastructure.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.embedded.netty.ReactorNettyHttpServerCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class NettyCustomizer(
    @Value("\${app.http.max-in-memory-bytes:16777216}") private val maxInMemoryBytes: Int,
    @Value("\${app.netty.max-initial-line-length:4096}") private val maxInitialLineLength: Int,
    @Value("\${app.netty.max-header-size:8192}") private val maxHeaderSize: Int
) {
    @Bean
    fun reactorCustomizer(): ReactorNettyHttpServerCustomizer {
        return ReactorNettyHttpServerCustomizer { httpServer ->
            httpServer.httpRequestDecoder { it.maxInitialLineLength(maxInitialLineLength).maxHeaderSize(maxHeaderSize) }
        }
    }
}
