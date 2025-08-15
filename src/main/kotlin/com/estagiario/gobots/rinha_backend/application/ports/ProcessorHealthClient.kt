package com.estagiario.gobots.rinha_backend.application.ports

import com.estagiario.gobots.rinha_backend.application.dto.ProcessorHealthResponse
import reactor.core.publisher.Mono // ✅ IMPORT CORRIGIDO

/**
 * Porta para um cliente HTTP que verifica a saúde de um processador.
 */
interface ProcessorHealthClient {
    fun checkHealth(): Mono<ProcessorHealthResponse>
}