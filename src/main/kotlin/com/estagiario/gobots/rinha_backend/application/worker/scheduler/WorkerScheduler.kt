package com.estagiario.gobots.rinha_backend.application.worker.scheduler

import com.estagiario.gobots.rinha_backend.application.worker.impl.PaymentProcessorWorkerImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.*

private val logger = KotlinLogging.logger {}

@Configuration
class WorkerScheduler {

    @Bean
    fun paymentWorkerRunner(
        worker: PaymentProcessorWorkerImpl,
        applicationScope: CoroutineScope,
        @Value("\${app.instance-id:API-LOCAL}") instanceId: String
    ): ApplicationRunner = ApplicationRunner {

        val workerId = "$instanceId-${UUID.randomUUID().toString().take(8)}"

        applicationScope.launch {
            logger.info { "🚀 Starting payment processor worker: $workerId" }

            while (true) {
                try {
                    worker.tick(workerId).block()
                    delay(500) // 500ms entre lotes
                } catch (e: Exception) {
                    logger.warn(e) { "Worker tick failed: ${e.message}" }
                    delay(2000) // Back-off em caso de erro
                }
            }
        }
    }
}