// ATUALIZE: src/main/kotlin/com/estagiario/gobots/rinha_backend/infrastructure/outgoing/repository/PaymentEventRepository.kt
package com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository

// ATUALIZE: src/main/kotlin/com/estagiario/gobots/rinha_backend/infrastructure/outgoing/repository/PaymentEventRepository.kt
// ATUALIZE: src/main/kotlin/com/estagiario/gobots/rinha_backend/infrastructure/outgoing/repository/PaymentEventRepository.kt
import com.estagiario.gobots.rinha_backend.domain.PaymentEvent
import com.estagiario.gobots.rinha_backend.domain.PaymentEventStatus
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Flux

interface PaymentEventRepository : ReactiveMongoRepository<com.estagiario.gobots.rinha_backend.domain.PaymentEvent, String> {
    fun findAllByStatus(status: com.estagiario.gobots.rinha_backend.domain.PaymentEventStatus): Flux<com.estagiario.gobots.rinha_backend.domain.PaymentEvent>
    fun findTop50ByStatusOrderByCreatedAtAsc(status: PaymentEventStatus): Flux<PaymentEvent>
}