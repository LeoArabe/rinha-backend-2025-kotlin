package com.estagiario.gobots.rinha_backend.application.worker.impl

import com.estagiario.gobots.rinha_backend.application.client.ProcessorClient
import com.estagiario.gobots.rinha_backend.application.worker.HealthCheckWorker
import com.estagiario.gobots.rinha_backend.infrastructure.client.dto.ProcessorHealthResponse
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import mu.KotlinLogging
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class HealthCheckWorkerImpl(
    private val processorClient: ProcessorClient,
    private val redisTemplate: ReactiveStringRedisTemplate
    // Adicionar aqui as configurações do application.yml via @Value se necessário
) : HealthCheckWorker {

    private val logger = KotlinLogging.logger {}
    private val instanceId = "API_INSTANCE_${java.util.UUID.randomUUID()}" // ID único para esta instância
    private val leaderKey = "rinha:leader:health-checker"

    @Scheduled(fixedDelay = 5000) // Executa a cada 5 segundos
    override suspend fun monitorProcessorsHealth() {
        if (tryToBecomeLeader()) {
            logger.info { "Instância $instanceId é o líder. Executando health check..." }
            try {
                coroutineScope {
                    launch { checkProcessorHealth("default", processorClient::checkDefaultProcessorHealth) }
                    launch { checkProcessorHealth("fallback", processorClient::checkFallbackProcessorHealth) }
                }
            } finally {
                // Embora o TTL do Redis lide com isso, é uma boa prática liberar o lock
                // redisTemplate.delete(leaderKey).subscribe()
            }
        } else {
            logger.debug { "Instância $instanceId não é o líder. Pulando health check." }
        }
    }

    private suspend fun tryToBecomeLeader(): Boolean {
        // Tenta adquirir um lock no Redis com um TTL (Time-To-Live).
        // A opção "setIfAbsent" garante que apenas uma instância consiga o lock.
        return redisTemplate.opsForValue()
            .setIfAbsent(leaderKey, instanceId, Duration.ofSeconds(10))
            .block() ?: false
    }

    private suspend fun checkProcessorHealth(
        processorName: String,
        healthCheckCall: suspend () -> ProcessorHealthResponse
    ) {
        val healthKey = "processor:$processorName:health"
        try {
            val health = healthCheckCall()
            // Salva o estado de saúde como um JSON simples no Redis com um TTL
            val healthJson = """{"failing":${health.failing},"minResponseTime":${health.minResponseTime}}"""
            redisTemplate.opsForValue().set(healthKey, healthJson, Duration.ofSeconds(15)).subscribe()
            logger.debug { "Saúde do processador $processorName atualizada: $healthJson" }
        } catch (e: Exception) {
            logger.warn(e) { "Falha ao verificar saúde do processador $processorName. Marcando como indisponível." }
            val healthJson = """{"failing":true,"minResponseTime":-1}"""
            redisTemplate.opsForValue().set(healthKey, healthJson, Duration.ofSeconds(15)).subscribe()
        }
    }
}