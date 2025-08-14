// application/worker/scheduler/WorkerScheduler.kt
package com.estagiario.gobots.rinha_backend.application.worker.scheduler

import com.estagiario.gobots.rinha_backend.application.worker.impl.PaymentProcessorWorkerImpl
import kotlinx.coroutines.CoroutineScope
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import reactor.core.publisher.Flux
import java.time.Duration
import java.util.UUID

private val logger = KotlinLogging.logger {}

@Configuration
class WorkerScheduler {

    @Bean
    fun paymentWorkerRunner(
        worker: PaymentProcessorWorkerImpl,
        @Value("\${app.instance-id:API-LOCAL}") instanceId: String,
        @Value("\${app.worker.interval-ms:500}") intervalMs: Long
    ): ApplicationRunner = ApplicationRunner {
        val owner = "$instanceId-${UUID.randomUUID().toString().take(8)}"
        logger.info { "🚀 Starting payment processor worker: $owner" }

        Flux.interval(Duration.ZERO, Duration.ofMillis(intervalMs))
            .concatMap { worker.tick(owner) }
            .onErrorContinue { t, _ -> logger.warn(t) { "Worker tick failed: ${t.message}" } }
            .subscribe()
    }
}
