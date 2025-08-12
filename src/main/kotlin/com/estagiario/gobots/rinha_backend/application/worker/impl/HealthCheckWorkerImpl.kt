package com.estagiario.gobots.rinha_backend.application.worker

import com.estagiario.gobots.rinha_backend.application.client.ProcessorClient
import com.estagiario.gobots.rinha_backend.domain.HealthCheckState
import com.estagiario.gobots.rinha_backend.domain.LeaderLock
import kotlinx.coroutines.reactive.awaitFirst
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

private val logger = KotlinLogging.logger {}

@Component
class HealthCheckWorkerImpl(
    private val processorClient: ProcessorClient,
    private val mongoTemplate: ReactiveMongoTemplate,
    @Value("\${app.instance-id:API-LOCAL}") private val instanceId: String,
    @Value("\${app.health-check.leader-ttl-seconds:10}") private val leaderTtlSeconds: Long,
    @Value("\${app.health-check.timeout-seconds:8}") private val healthCheckTimeoutSeconds: Long
) : HealthCheckWorker {

    companion object {
        private val _defaultProcessorState = AtomicReference(HealthCheckState.HEALTHY)
        private val _fallbackProcessorState = AtomicReference(HealthCheckState.HEALTHY)

        fun getDefaultProcessorState(): HealthCheckState = _defaultProcessorState.get()
        fun getFallbackProcessorState(): HealthCheckState = _fallbackProcessorState.get()
    }

    @Scheduled(fixedDelayString = "\${app.health-check.delay-ms:5000}")
    fun scheduleHealthCheck() {
        tryToBecomeLeader()
            .filter { isLeader -> isLeader }
            .flatMap { monitorProcessorsHealthReactive() }
            .doOnError { error -> logger.warn(error) { "Health check process failed unexpectedly." } }
            .onErrorResume { Mono.empty() }
            .subscribe()
    }

    override suspend fun monitorProcessorsHealth() {
        try {
            monitorProcessorsHealthReactive().awaitFirst()
        } catch (e: Exception) {
            logger.warn(e) { "Health check monitoring failed" }
        }
    }

    private fun monitorProcessorsHealthReactive(): Mono<Void> {
        logger.info { "Instance '$instanceId' is the leader. Performing health checks..." }

        val checkDefault = processorClient.checkDefaultProcessorHealth()
            .map { HealthCheckState.HEALTHY }
            .onErrorResume { Mono.just(HealthCheckState.UNHEALTHY) }
            .doOnSuccess { state ->
                _defaultProcessorState.set(state)
                logger.info { "Default processor health status: $state" }
            }

        val checkFallback = processorClient.checkFallbackProcessorHealth()
            .map { HealthCheckState.HEALTHY }
            .onErrorResume { Mono.just(HealthCheckState.UNHEALTHY) }
            .doOnSuccess { state ->
                _fallbackProcessorState.set(state)
                logger.info { "Fallback processor health status: $state" }
            }

        return Mono.zip(checkDefault, checkFallback)
            .timeout(Duration.ofSeconds(healthCheckTimeoutSeconds))
            .doOnError { logger.warn(it) { "Health check monitoring timed out or failed." } }
            .then()
    }

    private fun tryToBecomeLeader(): Mono<Boolean> {
        val now = Instant.now()
        val expireAt = now.plusSeconds(leaderTtlSeconds)
        val lockKey = "health-check-leader"

        val query = Query.query(
            Criteria.where("_id").`is`(lockKey)
                .and("expireAt").lt(now)
        )

        val update = Update()
            .set("owner", instanceId)
            .set("expireAt", expireAt)

        val options = FindAndModifyOptions.options().returnNew(true).upsert(true)

        return mongoTemplate.findAndModify(query, update, options, LeaderLock::class.java)
            .map { updatedLock -> updatedLock.owner == instanceId }
            .doOnSuccess { isLeader ->
                if (isLeader) {
                    logger.debug { "Instance '$instanceId' successfully acquired or refreshed leadership." }
                } else {
                    logger.debug { "Instance '$instanceId' failed to acquire leadership." }
                }
            }
            .defaultIfEmpty(false)
            .onErrorReturn(false)
    }
}