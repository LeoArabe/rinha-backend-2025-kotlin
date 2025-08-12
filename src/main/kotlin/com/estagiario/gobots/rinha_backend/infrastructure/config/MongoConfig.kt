package com.estagiario.gobots.rinha_backend.infrastructure.config

import mu.KotlinLogging
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition
import org.springframework.data.mongodb.core.index.Index
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
        val paymentIndexOps = mongoTemplate.indexOps("payments")

        val correlationIdIndex = Index()
            .on("correlationId", Sort.Direction.ASC)
            .unique()
            .named("correlation_idx")

        val summaryIndex = CompoundIndexDefinition(
            org.bson.Document(mapOf(
                "status" to 1,
                "lastUpdatedAt" to 1,
                "processorUsed" to 1
            ))
        ).named("summary_idx")

        return Flux.concat(
            paymentIndexOps.ensureIndex(correlationIdIndex),
            paymentIndexOps.ensureIndex(summaryIndex)
        ).doOnNext { logger.info { "Index ensured on 'payments': $it" } }
    }

    private fun createPaymentEventIndexes(mongoTemplate: ReactiveMongoTemplate): Flux<String> {
        // Garante índices em ambas as coleções possíveis (compatibilidade)
        val eventCollections = listOf("payment_events", "payment_outbox")

        return Flux.fromIterable(eventCollections)
            .flatMap { col ->
                val eventIndexOps = mongoTemplate.indexOps(col)
                val outboxProcessingIndex = CompoundIndexDefinition(
                    org.bson.Document(mapOf(
                        "status" to 1,
                        "createdAt" to 1
                    ))
                ).named("status_created_at_idx")

                val correlationIndex = Index()
                    .on("correlationId", Sort.Direction.ASC)
                    .named("event_correlation_idx")

                val cleanupIndex = CompoundIndexDefinition(
                    org.bson.Document(mapOf(
                        "status" to 1,
                        "processedAt" to 1
                    ))
                ).named("event_cleanup_idx")

                Flux.concat(
                    eventIndexOps.ensureIndex(outboxProcessingIndex),
                    eventIndexOps.ensureIndex(correlationIndex),
                    eventIndexOps.ensureIndex(cleanupIndex)
                ).doOnNext { logger.info { "Index ensured on '$col': $it" } }
            }
    }

    private fun createLeaderLockIndexes(mongoTemplate: ReactiveMongoTemplate): Flux<String> {
        val lockIndexOps = mongoTemplate.indexOps("leader_locks")

        val lockTtlIndex = Index()
            .on("expireAt", Sort.Direction.ASC)
            .expire(java.time.Duration.ZERO)
            .named("ttl_idx")

        val leaderElectionIndex = CompoundIndexDefinition(
            org.bson.Document(mapOf(
                "_id" to 1,
                "expireAt" to 1,
                "owner" to 1
            ))
        ).named("leader_election_idx")

        return Flux.concat(
            lockIndexOps.ensureIndex(leaderElectionIndex),
            lockIndexOps.ensureIndex(lockTtlIndex)
        ).doOnNext { logger.info { "Index ensured on 'leader_locks': $it" } }
    }
}
