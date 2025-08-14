package com.estagiario.gobots.rinha_backend.application.worker

import com.estagiario.gobots.rinha_backend.domain.PaymentEvent
import com.estagiario.gobots.rinha_backend.domain.PaymentEventStatus
import com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository.PaymentEventRepository
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

@Component
class OutboxRelay(
    private val template: ReactiveMongoTemplate,
    private val paymentEventRepository: PaymentEventRepository
) {
    fun claimBatch(ownerId: String, limit: Int): Flux<PaymentEvent> {
        val now = Instant.now()

        // ✅ Uses the new, cleaner repository method
        return paymentEventRepository.findPendingEvents(PaymentEventStatus.PENDING, now)
            .take(limit.toLong())
            .collectList()
            .flatMapMany { events ->
                if (events.isEmpty()) {
                    return@flatMapMany Flux.empty()
                }

                val idsToClaim = events.mapNotNull { it.id }
                val claimUpdate = Update()
                    .set("status", PaymentEventStatus.PROCESSING.name)
                    .set("owner", ownerId)
                    .set("processingAt", now)

                val claimQuery = Query.query(Criteria.where("_id").`in`(idsToClaim))

                template.updateMulti(claimQuery, claimUpdate, PaymentEvent::class.java)
                    .flatMapMany { result ->
                        if (result.modifiedCount > 0) {
                            Flux.fromIterable(events.filter { idsToClaim.contains(it.id) })
                                .map { it.markAsProcessing(ownerId) }
                        } else {
                            Flux.empty()
                        }
                    }
            }
    }

    fun markAsProcessed(event: PaymentEvent): Mono<PaymentEvent> {
        return paymentEventRepository.save(event.markAsProcessed())
    }

    fun scheduleForRetry(event: PaymentEvent, delaySeconds: Long, error: String?): Mono<PaymentEvent> {
        return paymentEventRepository.save(event.scheduleForRetry(delaySeconds, error))
    }
}