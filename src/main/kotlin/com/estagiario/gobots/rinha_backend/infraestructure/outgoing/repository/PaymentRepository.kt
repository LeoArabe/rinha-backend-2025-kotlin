package com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository

import com.estagiario.gobots.rinha_backend.domain.Payment
import kotlinx.coroutines.flow.Flow
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository

@Repository
interface PaymentRepository : CoroutineCrudRepository<Payment, String> {
    fun findAllByCorrelationIdIn(correlationIds: List<String>): Flow<Payment>
}