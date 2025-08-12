package com.estagiario.gobots.rinha_backend.infrastructure.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory
import org.springframework.boot.web.server.WebServerFactoryCustomizer
import org.springframework.stereotype.Component
import reactor.netty.http.server.HttpServer
import reactor.netty.http.server.HttpRequestDecoderSpec

@Component
class NettyCustomizer(
    @Value("\${app.http.max-in-memory-bytes:16777216}") private val maxInMemoryBytes: Int,
    @Value("\${app.netty.max-initial-line-length:4096}") private val maxInitialLineLength: Int,
    @Value("\${app.netty.max-header-size:8192}") private val maxHeaderSize: Int
) : WebServerFactoryCustomizer<NettyReactiveWebServerFactory> {

    override fun customize(factory: NettyReactiveWebServerFactory) {
        factory.addServerCustomizers({ httpServer: HttpServer ->
            httpServer.httpRequestDecoder { spec: HttpRequestDecoderSpec ->
                spec.maxInitialLineLength(maxInitialLineLength)
                    .maxHeaderSize(maxHeaderSize)
            }
        })
    }
}