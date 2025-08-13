package com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository

import com.estagiario.gobots.rinha_backend.domain.PaymentEvent
import com.estagiario.gobots.rinha_backend.domain.PaymentEventStatus // ✅ Importe seu Enum
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux

@Repository
interface PaymentEventRepository : ReactiveMongoRepository<PaymentEvent, String> {
    // ✅ MÉTODO ADICIONADO: Busca eventos pendentes, ordenados pelo mais antigo
    fun findByStatusOrderByCreatedAtAsc(status: PaymentEventStatus): Flux<PaymentEvent>
}