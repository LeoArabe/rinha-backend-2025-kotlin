package com.estagiario.gobots.rinha_backend.application.worker

import com.estagiario.gobots.rinha_backend.application.client.ProcessorClient
import com.estagiario.gobots.rinha_backend.domain.HealthCheckState
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

@Component
class HealthCheckWorkerImpl(
    private val processorClient: ProcessorClient,
    private val mongoTemplate: ReactiveMongoTemplate,
    @Value("\${app.instance-id:API-LOCAL}") private val instanceId: String,
    @Value("\${app.health-check.leader-ttl-seconds:10}") private val leaderTtlSeconds: Long
) {

    companion object {
        val defaultProcessorState = AtomicReference(HealthCheckState.HEALTHY)
        val fallbackProcessorState = AtomicReference(HealthCheckState.HEALTHY)
    }

    @Scheduled(fixedDelayString = "\${app.health-check.delay-ms:5000}")
    fun scheduleHealthCheck() {
        tryToBecomeLeader()
            .filter { it }
            .flatMap { monitorProcessorsHealth() }
            .onErrorResume { Mono.empty() }
            .subscribe()
    }

    private fun monitorProcessorsHealth(): Mono<Void> {
        val checkDefault = processorClient.checkDefaultProcessorHealth()
            .map { HealthCheckState.HEALTHY }
            .onErrorResume { Mono.just(HealthCheckState.UNHEALTHY) }
            .doOnSuccess { defaultProcessorState.set(it) }

        val checkFallback = processorClient.checkFallbackProcessorHealth()
            .map { HealthCheckState.HEALTHY }
            .onErrorResume { Mono.just(HealthCheckState.UNHEALTHY) }
            .doOnSuccess { fallbackProcessorState.set(it) }

        return Mono.zip(checkDefault, checkFallback).then()
    }

    private fun tryToBecomeLeader(): Mono<Boolean> {
        val now = Instant.now()
        val expireAt = now.plusSeconds(leaderTtlSeconds)

        val query = Query.query(
            Criteria.where("_id").`is`("health-check") // key document id
                .orOperator(
                    Criteria.where("expireAt").lt(now),
                    Criteria.where("owner").`is`(instanceId)
                )
        )

        val update = Update()
            .set("owner", instanceId)
            .set("expireAt", expireAt)

        val options = FindAndModifyOptions.options().returnNew(true).upsert(true)

        return mongoTemplate.findAndModify(query, update, options, LeaderLock::class.java)
            .map { it.owner == instanceId }
            .onErrorReturn(false)
    }
}
