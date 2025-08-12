package com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository

import com.estagiario.gobots.rinha_backend.domain.PaymentEvent
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import org.springframework.stereotype.Repository

@Repository
interface PaymentEventRepository : ReactiveMongoRepository<PaymentEvent, String>