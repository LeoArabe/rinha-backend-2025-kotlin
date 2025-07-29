package com.estagiario.gobots.rinha_backend.infrastructure.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory
import org.springframework.data.mongodb.ReactiveMongoTransactionManager
import org.springframework.transaction.reactive.TransactionalOperator

@Configuration
class TransactionConfig {

    @Bean
    fun reactiveMongoTransactionManager(
        mongoDatabaseFactory: ReactiveMongoDatabaseFactory
    ): ReactiveMongoTransactionManager {
        return ReactiveMongoTransactionManager(mongoDatabaseFactory)
    }

    @Bean
    fun transactionalOperator(
        reactiveMongoTransactionManager: ReactiveMongoTransactionManager
    ): TransactionalOperator {
        return TransactionalOperator.create(reactiveMongoTransactionManager)
    }
}