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
            .writeConcern(WriteConcern.W1)
            .readConcern(ReadConcern.AVAILABLE)
            .readPreference(ReadPreference.primaryPreferred())
            .applyToConnectionPoolSettings {
                it.maxSize(20).minSize(5)
                    .maxWaitTime(2, TimeUnit.SECONDS)
                    .maxConnectionIdleTime(30, TimeUnit.SECONDS)
            }
            .applyToSocketSettings {
                it.connectTimeout(2, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
            }
    }

    @Bean
    fun initializeOptimizedIndexes(mongoTemplate: ReactiveMongoTemplate): ApplicationRunner {
        return ApplicationRunner {
            Flux.concat(
                createPaymentIndexes(mongoTemplate),
                createPaymentEventIndexes(mongoTemplate),
                createProcessorHealthIndexes(mongoTemplate)
            ).then()
                .subscribe(
                    { logger.info { "✅ All MongoDB indexes created successfully" } },
                    { error -> logger.error(error) { "🔥 Failed to create MongoDB indexes" } }
                )
        }
    }

    private fun createPaymentIndexes(mongoTemplate: ReactiveMongoTemplate): Flux<String> {
        val ops = mongoTemplate.indexOps("payments")
        val correlationIdIndex = Index().on("correlationId", Sort.Direction.ASC).unique()
        val summaryIndex = CompoundIndexDefinition(
            org.bson.Document(mapOf("status" to 1, "requestedAt" to 1, "processorUsed" to 1))
        )
        return Flux.concat(
            ops.ensureIndex(correlationIdIndex),
            ops.ensureIndex(summaryIndex)
        )
    }

    private fun createPaymentEventIndexes(mongoTemplate: ReactiveMongoTemplate): Flux<String> {
        val ops = mongoTemplate.indexOps("payment_events")
        val outboxIndex = CompoundIndexDefinition(
            org.bson.Document(mapOf("status" to 1, "createdAt" to 1))
        )
        val correlationIndex = Index().on("correlationId", Sort.Direction.ASC)
        return Flux.concat(
            ops.ensureIndex(outboxIndex),
            ops.ensureIndex(correlationIndex)
        )
    }

    private fun createProcessorHealthIndexes(mongoTemplate: ReactiveMongoTemplate): Flux<String> {
        val ops = mongoTemplate.indexOps("processor_health")
        val ttlIndex = Index()
            .on("expireAt", Sort.Direction.ASC)
            .expire(java.time.Duration.ZERO)
        return ops.ensureIndex(ttlIndex).flux()
    }
}
