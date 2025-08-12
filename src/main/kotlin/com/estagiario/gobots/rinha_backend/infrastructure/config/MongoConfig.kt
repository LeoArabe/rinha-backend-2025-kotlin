package com.estagiario.gobots.rinha_backend.infrastructure.config

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.connection.ConnectionPoolSettings
import com.mongodb.reactivestreams.client.MongoClients
import com.mongodb.reactivestreams.client.MongoClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.TimeUnit

@Configuration
class MongoConfig(
    @Value("\${spring.data.mongodb.uri}") private val uri: String,
    @Value("\${spring.data.mongodb.pool.min-size:3}") private val minPoolSize: Int,
    @Value("\${spring.data.mongodb.pool.max-size:15}") private val maxPoolSize: Int,
    @Value("\${spring.data.mongodb.pool.max-wait-time-ms:5000}") private val maxWaitMs: Long
) {
    @Bean
    fun reactiveMongoClient(): MongoClient {
        val conn = ConnectionString(uri)
        val poolSettings = ConnectionPoolSettings.builder()
            .minSize(minPoolSize)
            .maxSize(maxPoolSize)
            .maxWaitTime(maxWaitMs, TimeUnit.MILLISECONDS)
            .build()

        val settings = MongoClientSettings.builder()
            .applyConnectionString(conn)
            .applyToConnectionPoolSettings { it.applySettings(poolSettings) }
            .build()

        return MongoClients.create(settings)
    }
}
