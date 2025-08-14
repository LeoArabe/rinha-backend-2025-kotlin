package com.estagiario.gobots.rinha_backend.infrastructure.outgoing.adapter

import com.estagiario.gobots.rinha_backend.application.ports.ProcessorHealthUpdater
import com.estagiario.gobots.rinha_backend.domain.ProcessorHealth
import com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository.ProcessorHealthRepository
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * Adapter que implementa a porta ProcessorHealthUpdater usando o Spring Data MongoDB.
 */
@Component
class MongoProcessorHealthUpdater(
    private val repository: ProcessorHealthRepository
) : ProcessorHealthUpdater {

    override fun findAll(): Flux<ProcessorHealth> {
        return repository.findAll()
    }

    override fun save(health: ProcessorHealth): Mono<Void> {
        return repository.save(health).then()
    }
}
