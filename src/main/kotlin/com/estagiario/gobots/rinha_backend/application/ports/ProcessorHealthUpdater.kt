package com.estagiario.gobots.rinha_backend.application.ports

import com.estagiario.gobots.rinha_backend.domain.ProcessorHealth
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * Porta de saída para persistir o estado de saúde dos processadores.
 * A camada de domínio (service) usa esta porta, e a camada de infraestrutura (adapter) a implementa.
 */
interface ProcessorHealthUpdater {

    /**
     * Busca todos os registros de saúde de processadores.
     */
    fun findAll(): Flux<ProcessorHealth>

    /**
     * Salva ou atualiza o estado de saúde de um processador.
     *
     * @param health O objeto ProcessorHealth a ser salvo.
     * @return um Mono que completa quando a operação termina.
     */
    fun save(health: ProcessorHealth): Mono<Void>
}
