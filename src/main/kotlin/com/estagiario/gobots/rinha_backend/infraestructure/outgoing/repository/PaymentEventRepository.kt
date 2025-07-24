package com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository

import com.estagiario.gobots.rinha_backend.domain.PaymentEvent
import com.estagiario.gobots.rinha_backend.domain.PaymentEventStatus
import kotlinx.coroutines.flow.Flow
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository

@Repository
interface PaymentEventRepository : CoroutineCrudRepository<PaymentEvent, String> {
    /**
     * Busca eventos pendentes para serem processados pelo OutboxRelay.
     */
    fun findAllByStatus(status: PaymentEventStatus): Flow<PaymentEvent>
}