package com.estagiario.gobots.rinha_backend.application.worker.scheduler

import com.estagiario.gobots.rinha_backend.application.service.HealthCheckService
import mu.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean

private val logger = KotlinLogging.logger {}

@Component
class HealthCheckScheduler(
    private val healthCheckService: HealthCheckService
) {
    // Trava simples para evitar execuções sobrepostas
    private val isRunning = AtomicBoolean(false)

    @Scheduled(fixedDelayString = "\${app.health-check.delay-ms:2000}")
    fun runHealthChecks() {
        if (isRunning.compareAndSet(false, true)) {
            healthCheckService.monitorProcessors()
                .doOnError { error -> logger.warn(error) { "Scheduled health check failed unexpectedly." } }
                .doFinally { isRunning.set(false) }
                .subscribe() // "Fire-and-forget"
        }
    }
}
