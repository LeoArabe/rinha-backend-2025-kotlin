package com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository

import com.estagiario.gobots.rinha_backend.domain.Payment
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Repository
interface PaymentRepository : ReactiveMongoRepository<Payment, String> {
    fun findAllByCorrelationIdIn(correlationIds: List<String>): Flux<Payment>
    fun findByCorrelationId(correlationId: String): Mono<Payment>
}
