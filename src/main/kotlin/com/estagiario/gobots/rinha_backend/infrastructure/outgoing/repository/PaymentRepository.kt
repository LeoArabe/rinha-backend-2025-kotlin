// ATUALIZE: src/main/kotlin/com/estagiario/gobots/rinha_backend/infrastructure/outgoing/repository/PaymentRepository.kt
package com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository

// ATUALIZE: src/main/kotlin/com/estagiario/gobots/rinha_backend/infrastructure/outgoing/repository/PaymentRepository.kt
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Flux

interface PaymentRepository : ReactiveMongoRepository<com.estagiario.gobots.rinha_backend.domain.Payment, String> {
    fun findAllByCorrelationIdIn(correlationIds: List<String>): Flux<com.estagiario.gobots.rinha_backend.domain.Payment>
}