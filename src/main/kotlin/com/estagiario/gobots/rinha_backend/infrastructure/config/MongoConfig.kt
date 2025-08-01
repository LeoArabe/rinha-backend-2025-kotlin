package com.estagiario.gobots.rinha_backend.infrastructure.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory
import org.springframework.data.mongodb.ReactiveMongoTransactionManager
import org.springframework.transaction.ReactiveTransactionManager
import org.springframework.transaction.annotation.EnableTransactionManagement // ✅ ADICIONE ESTA IMPORTAÇÃO

@Configuration
@EnableTransactionManagement // ✅ ADICIONE ESTA ANOTAÇÃO PARA ATIVAR @Transactional
class TransactionConfig {

    @Bean
    fun reactiveMongoTransactionManager(
        databaseFactory: ReactiveMongoDatabaseFactory
    ): ReactiveTransactionManager {
        return ReactiveMongoTransactionManager(databaseFactory)
    }

    // ❌ O bean do TransactionalOperator não é mais necessário aqui
}