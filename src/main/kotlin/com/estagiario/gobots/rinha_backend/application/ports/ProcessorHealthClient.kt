package com.estagiario.gobots.rinha_backend.application.ports

interface ProcessorHealthClient {
    /**
     * Verifica a saúde do processador.
     * @return Mono<Pair<Boolean, Long?>> onde Boolean é isHealthy e Long é latência em ms
     */
    fun checkHealth(): Mono<Pair<Boolean, Long?>>
}