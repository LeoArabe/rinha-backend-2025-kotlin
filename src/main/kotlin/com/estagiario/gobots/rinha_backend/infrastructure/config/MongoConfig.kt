package com.estagiario.gobots.rinha_backend.infrastructure.config

import mu.KotlinLogging
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.core.index.ReactiveIndexOperations
import org.springframework.data.mongodb.core.query.Criteria
import reactor.core.publisher.Flux

private val logger = KotlinLogging.logger {}

@Configuration
class MongoConfig {

    @Bean
    fun initializeIndexes(mongoTemplate: ReactiveMongoTemplate): ApplicationRunner {
        return ApplicationRunner {
            val allIndexes = Flux.concat(
                createPaymentIndexes(mongoTemplate),
                createPaymentEventIndexes(mongoTemplate),
                createLeaderLockIndexes(mongoTemplate)
            )

            allIndexes.then()
                .subscribe(
                    { logger.info { "✅ All MongoDB indexes created successfully" } },
                    { error -> logger.error(error) { "🔥 Failed to create MongoDB indexes" } }
                )
        }
    }

    private fun createPaymentIndexes(mongoTemplate: ReactiveMongoTemplate): Flux<String> {
        val paymentIndexOps: ReactiveIndexOperations = mongoTemplate.indexOps("payments")

        val correlationIdIndex = Index()
            .on("correlationId", Sort.Direction.ASC)
            .unique()
            .named("correlation_id_unique_idx")

        val summaryIndex = CompoundIndexDefinition(
            org.bson.Document(mapOf(
                "status" to 1,
                "lastUpdatedAt" to 1,
                "processorUsed" to 1
            ))
        ).named("summary_query_idx")

        val retryIndex = CompoundIndexDefinition(
            org.bson.Document(mapOf(
                "status" to 1,
                "nextRetryAt" to 1
            ))
        ).named("retry_query_idx")

        return Flux.concat(
            paymentIndexOps.ensureIndex(correlationIdIndex),
            paymentIndexOps.ensureIndex(summaryIndex),
            paymentIndexOps.ensureIndex(retryIndex)
        ).doOnNext { logger.info { "Index ensured on 'payments': $it" } }
    }

    private fun createPaymentEventIndexes(mongoTemplate: ReactiveMongoTemplate): Flux<String> {
        val eventIndexOps: ReactiveIndexOperations = mongoTemplate.indexOps("payment_events")

        val outboxProcessingIndex = CompoundIndexDefinition(
            org.bson.Document(mapOf(
                "status" to 1,
                "owner" to 1,
                "nextRetryAt" to 1,
                "createdAt" to 1
            ))
        ).named("outbox_processing_idx")

        val correlationIndex = Index()
            .on("correlationId", Sort.Direction.ASC)
            .named("event_correlation_idx")

        val cleanupIndex = CompoundIndexDefinition(
            org.bson.Document(mapOf(
                "status" to 1,
                "processedAt" to 1
            ))
        ).named("event_cleanup_idx")

        // ✅ CORREÇÃO: Usar PartialIndexFilter em vez de Query
        val ttlIndex = Index()
            .on("processedAt", Sort.Direction.ASC)
            .expire(java.time.Duration.ofDays(30))
            .named("processed_events_ttl_idx")
            .partial(
                org.springframework.data.mongodb.core.index.PartialIndexFilter.of(
                    Criteria.where("status").`is`("PROCESSED")
                )
            )

        return Flux.concat(
            eventIndexOps.ensureIndex(outboxProcessingIndex),
            eventIndexOps.ensureIndex(correlationIndex),
            eventIndexOps.ensureIndex(cleanupIndex),
            eventIndexOps.ensureIndex(ttlIndex)
        ).doOnNext { logger.info { "Index ensured on 'payment_events': $it" } }
    }

    private fun createLeaderLockIndexes(mongoTemplate: ReactiveMongoTemplate): Flux<String> {
        val lockIndexOps: ReactiveIndexOperations = mongoTemplate.indexOps("leader_locks")

        val leaderElectionIndex = CompoundIndexDefinition(
            org.bson.Document(mapOf(
                "_id" to 1,
                "expireAt" to 1,
                "owner" to 1
            ))
        ).named("leader_election_idx")

        val lockTtlIndex = Index()
            .on("expireAt", Sort.Direction.ASC)
            .expire(java.time.Duration.ZERO)
            .named("leader_lock_ttl_idx")

        return Flux.concat(
            lockIndexOps.ensureIndex(leaderElectionIndex),
            lockIndexOps.ensureIndex(lockTtlIndex)
        ).doOnNext { logger.info { "Index ensured on 'leader_locks': $it" } }
    }
}