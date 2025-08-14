package com.estagiario.gobots.rinha_backend.application.service

import com.estagiario.gobots.rinha_backend.application.ports.ProcessorHealthClient
import com.estagiario.gobots.rinha_backend.application.ports.ProcessorHealthUpdater
import com.estagiario.gobots.rinha_backend.domain.HealthCheckState
import com.estagiario.gobots.rinha_backend.domain.ProcessorHealth
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.Instant

private val logger = KotlinLogging.logger {}

@Service
class HealthCheckService(
    @Qualifier("defaultHealthClient") private val defaultHealthClient: ProcessorHealthClient,
    @Qualifier("fallbackHealthClient") private val fallbackHealthClient: ProcessorHealthClient,
    private val healthUpdater: ProcessorHealthUpdater,
    @Value("\${app.instance-id:API-LOCAL}") private val instanceId: String,
    @Value("\${app.health-check.touch-threshold-seconds:15}") private val touchThresholdSeconds: Long,
    @Value("\${app.health-check.timeout-seconds:3}") private val requestTimeoutSeconds: Long
) {

    /**
     * Orquestra a verificação de saúde dos processadores.
     * Pode ser chamado de forma agendada.
     */
    fun monitorProcessors(): Mono<Void> {
        val now = Instant.now()

        // Verifica se um "touch" (operação barata) é suficiente.
        return healthUpdater.findAll()
            .collectList()
            .flatMap { currentStatusList ->
                val defaultStatus = currentStatusList.find { it.processor == "default" }
                val fallbackStatus = currentStatusList.find { it.processor == "fallback" }

                val canTouch = defaultStatus != null && fallbackStatus != null &&
                        defaultStatus.state == HealthCheckState.HEALTHY &&
                        fallbackStatus.state == HealthCheckState.HEALTHY &&
                        defaultStatus.lastCheckedAt.isAfter(now.minusSeconds(touchThresholdSeconds)) &&
                        fallbackStatus.lastCheckedAt.isAfter(now.minusSeconds(touchThresholdSeconds))

                if (canTouch) {
                    // Operação barata: apenas atualiza o timestamp no banco.
                    val touchDefault = healthUpdater.save(defaultStatus!!.touch(instanceId))
                    val touchFallback = healthUpdater.save(fallbackStatus!!.touch(instanceId))
                    Mono.when(touchDefault, touchFallback)
                } else {
                    // Operação completa: faz chamadas HTTP para verificar a saúde.
                    val checkDefault = performFullCheck("default", defaultHealthClient)
                    val checkFallback = performFullCheck("fallback", fallbackHealthClient)
                    Mono.when(checkDefault, checkFallback)
                }
            }
    }

    private fun performFullCheck(processorName: String, client: ProcessorHealthClient): Mono<Void> {
        return client.checkHealth()
            .timeout(Duration.ofSeconds(requestTimeoutSeconds))
            .map { response ->
                val isHealthy = response.failing == false
                ProcessorHealth.newStatus(
                    processor = processorName,
                    state = if (isHealthy) HealthCheckState.HEALTHY else HealthCheckState.UNHEALTHY,
                    latencyMs = response.minResponseTime.toLong(),
                    checkedBy = instanceId
                )
            }
            .onErrorResume { error ->
                logger.warn(error) { "Health check call failed for processor '$processorName'" }
                Mono.just(
                    ProcessorHealth.newStatus(
                        processor = processorName,
                        state = HealthCheckState.UNHEALTHY,
                        latencyMs = null, // Latência desconhecida em caso de erro
                        checkedBy = instanceId
                    )
                )
            }
            .flatMap { newHealthStatus -> healthUpdater.save(newHealthStatus) }
    }
}
