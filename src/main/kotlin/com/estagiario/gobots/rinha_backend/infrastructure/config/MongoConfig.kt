package com.estagiario.gobots.rinha_backend.infrastructure.config

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.WriteConcern
import com.mongodb.ReadConcern
import com.mongodb.ReadPreference
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.config.AbstractReactiveMongoConfiguration
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition
import org.springframework.data.mongodb.core.index.Index
import reactor.core.publisher.Flux
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

@Configuration
class MongoOptimizedConfig : AbstractReactiveMongoConfiguration() {

    @Value("\${spring.data.mongodb.uri:mongodb://localhost:27017/payments}")
    private lateinit var mongoUri: String

    @Value("\${spring.data.mongodb.database:payments}")
    private lateinit var databaseName: String

    override fun getDatabaseName(): String = databaseName

    override fun configureClientSettings(builder: MongoClientSettings.Builder) {
        val connectionString = ConnectionString(mongoUri)

        builder.applyConnectionString(connectionString)
            .writeConcern(WriteConcern.W1) // ⚡ Performance: w:1 ao invés de majority
            .readConcern(ReadConcern.AVAILABLE) // ⚡ Less strict read consistency
            .readPreference(ReadPreference.primaryPreferred())
            .applyToConnectionPoolSettings { poolBuilder ->
                poolBuilder
                    .maxSize(20) // ⚡ Connection pool otimizado
                    .minSize(5)
                    .maxWaitTime(2, TimeUnit.SECONDS)
                    .maxConnectionIdleTime(30, TimeUnit.SECONDS)
            }
            .applyToSocketSettings { socketBuilder ->
                socketBuilder
                    .connectTimeout(2, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
            }
    }

    @Bean
    fun initializeOptimizedIndexes(mongoTemplate: ReactiveMongoTemplate): ApplicationRunner {
        return ApplicationRunner {
            val allIndexes = Flux.concat(
                createPaymentIndexes(mongoTemplate),
                createPaymentEventIndexes(mongoTemplate),
                createProcessorHealthIndexes(mongoTemplate),
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
            .named("correlation_unique_idx")

        // ⚡ Índice otimizado para summary queries
        val summaryIndex = CompoundIndexDefinition(
            org.bson.Document(mapOf(
                "status" to 1,
                "requestedAt" to 1,
                "processorUsed" to 1
            ))
        ).named("summary_optimized_idx")

        return Flux.concat(
            paymentIndexOps.ensureIndex(correlationIdIndex),
            paymentIndexOps.ensureIndex(summaryIndex)
        ).doOnNext { logger.info { "Index ensured on 'payments': $it" } }
    }

    private fun createPaymentEventIndexes(mongoTemplate: ReactiveMongoTemplate): Flux<String> {
        val eventIndexOps = mongoTemplate.indexOps("payment_events")

        // ⚡ Índice otimizado para outbox queries
        val outboxIndex = CompoundIndexDefinition(
            org.bson.Document(mapOf(
                "status" to 1,
                "createdAt" to 1
            ))
        ).named("outbox_processing_idx")

        val correlationIndex = Index()
            .on("correlationId", Sort.Direction.ASC)
            .named("event_correlation_idx")

        return Flux.concat(
            eventIndexOps.ensureIndex(outboxIndex),
            eventIndexOps.ensureIndex(correlationIndex)
        ).doOnNext { logger.info { "Index ensured on 'payment_events': $it" } }
    }

    private fun createProcessorHealthIndexes(mongoTemplate: ReactiveMongoTemplate): Flux<String> {
        val ops = mongoTemplate.indexOps("processor_health")
        val ttlIndex = Index()
            .on("expireAt", Sort.Direction.ASC)
            .expire(java.time.Duration.ZERO)
            .named("processor_health_ttl_idx")

        return ops.ensureIndex(ttlIndex)
            .doOnNext { logger.info { "Index ensured on 'processor_health': $it" } }
            .flux()
    }

    private fun createLeaderLockIndexes(mongoTemplate: ReactiveMongoTemplate): Flux<String> {
        val lockIndexOps = mongoTemplate.indexOps("leader_locks")

        val ttlIndex = Index()
            .on("expireAt", Sort.Direction.ASC)
            .expire(java.time.Duration.ZERO)
            .named("leader_lock_ttl_idx")

        return lockIndexOps.ensureIndex(ttlIndex)
            .doOnNext { logger.info { "Index ensured on 'leader_locks': $it" } }
            .flux()
    }
}