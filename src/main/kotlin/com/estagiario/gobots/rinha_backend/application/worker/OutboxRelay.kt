package com.estagiario.gobots.rinha_backend.application.worker

import com.estagiario.gobots.rinha_backend.domain.PaymentEvent
import com.estagiario.gobots.rinha_backend.domain.PaymentEventStatus
import mu.KotlinLogging
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.scheduler.Schedulers
import java.time.Instant

private val logger = KotlinLogging.logger {}

@Component
class OutboxRelayOptimized(
    private val mongo: ReactiveMongoTemplate
) {

    /**
     * ⚡ Claims pending events with atomic updates and parallel processing
     */
    fun claimPendingBatch(owner: String, limit: Int): Flux<PaymentEvent> {
        val now = Instant.now()

        // ⚡ Single query with compound criteria for better performance
        val criteria = Criteria().andOperator(
            Criteria.where("status").`is`(PaymentEventStatus.PENDING.name),
            Criteria().orOperator(
                Criteria.where("nextRetryAt").isNull,
                Criteria.where("nextRetryAt").lte(now)
            )
        )

        val query = Query.query(criteria)
            .limit(limit)
            .with(org.springframework.data.domain.Sort.by("createdAt").ascending())

        return mongo.find(query, PaymentEvent::class.java, "payment_events")
            .parallel(4) // ⚡ Parallel processing
            .runOn(Schedulers.boundedElastic())
            .flatMap { event -> attemptClaim(event, owner, now) }
            .sequential()
            .onErrorContinue { t, _ ->
                logger.warn(t) { "Error claiming event: ${t.message}" }
            }
    }

    private fun attemptClaim(event: PaymentEvent, owner: String, now: Instant): Flux<PaymentEvent> {
        val updateQuery = Query.query(
            Criteria.where("_id").`is`(event.id)
                .and("status").`is`(PaymentEventStatus.PENDING.name)
        )

        val update = Update()
            .set("status", PaymentEventStatus.PROCESSING.name)
            .set("processingAt", now)
            .set("owner", owner)

        return mongo.updateFirst(updateQuery, update, "payment_events")
            .filter { result -> result.modifiedCount == 1L }
            .map { event.claimForProcessing(owner) }
            .flux()
    }
}