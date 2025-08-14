package com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository

import com.estagiario.gobots.rinha_backend.domain.PaymentEvent
import com.estagiario.gobots.rinha_backend.domain.PaymentEventStatus
import org.springframework.data.mongodb.repository.Query
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import java.time.Instant

@Repository
interface PaymentEventRepository : ReactiveMongoRepository<PaymentEvent, String> {
    /**
     * Finds pending events that are ready to be processed, ordered by creation time.
     */
    @Query("{ 'status': ?0, 'nextRetryAt': { '\$lte': ?1 } }")
    fun findPendingEvents(status: PaymentEventStatus, before: Instant): Flux<PaymentEvent>
}