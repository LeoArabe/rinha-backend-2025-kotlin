// CRIE ESTE FICHEIRO EM:
// src/main/kotlin/com/estagiario/gobots/rinha_backend/infrastructure/config/CoroutineScopeConfig.kt

package com.estagiario.gobots.rinha_backend.infrastructure.config

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class CoroutineScopeConfig {
    @Bean
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}