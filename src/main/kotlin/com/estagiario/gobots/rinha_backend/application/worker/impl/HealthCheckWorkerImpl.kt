package com.estagiario.gobots.rinha_backend.application.worker.impl

import com.estagiario.gobots.rinha_backend.domain.HealthCheckState
import com.estagiario.gobots.rinha_backend.domain.LeaderLock
import com.estagiario.gobots.rinha_backend.domain.ProcessorHealth
import com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository.ProcessorHealthRepository
import mu.KotlinLogging
import org.bson.Document
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

private val logger = KotlinLogging.logger {}

@Component
class HealthCheckWorkerImpl(
    private val mongoTemplate: ReactiveMongoTemplate,
    private val processorHealthRepo: ProcessorHealthRepository,
    @Value("\${app.instance-id:API-LOCAL}") private val instanceId: String,
    @Value("\${app.health-check.leader-ttl-seconds:10}") private val leaderTtlSeconds: Long,
    @Value("\${app.health-check.delay-ms:2000}") private val baseDelayMs: Long,
    @Value("\${processor.default.url:http://payment-processor-default:8080}") private val defaultUrl: String,
    @Value("\${processor.fallback.url:http://payment-processor-fallback:8080}") private val fallbackUrl: String,
    @Value("\${app.health-check.touch-threshold-seconds:15}") private val touchThresholdSeconds: Long,
    @Value("\${app.health-check.timeout-seconds:3}") private val requestTimeoutSeconds: Long
) {

    private val defaultClient: WebClient = WebClient.builder().baseUrl(defaultUrl).build()
    private val fallbackClient: WebClient = WebClient.builder().baseUrl(fallbackUrl).build()

    // small guard to avoid overlapping runs
    private val running = AtomicBoolean(false)

    /**
     * Scheduled runner — tenta adquirir liderança antes de executar checks.
     * Usamos fixedDelay para simplicidade; a lógica interna faz 'touch' quando ambos saudáveis.
     */
    @Scheduled(fixedDelayString = "\${app.health-check.delay-ms:2000}")
    fun scheduleHealthCheck() {
        // avoid reentrancy
        if (!running.compareAndSet(false, true)) return

        tryToBecomeLeader()
            .filter { isLeader -> isLeader }
            .flatMap { monitorProcessorsHealthReactive() }
            .doOnError { e -> logger.warn(e) { "Health check failed (unexpected)." } }
            .onErrorResume { Mono.empty() }
            .doFinally { running.set(false) }
            .subscribe() // fire-and-forget, non-blocking
    }

    private fun monitorProcessorsHealthReactive(): Mono<Void> {
        val now = Instant.now()

        // If cached health shows both healthy recently, just "touch" (cheap) to reduce external calls.
        return processorHealthRepo.findAll()
            .collectList()
            .flatMap { list ->
                val default = list.find { it.processor == "default" }
                val fallback = list.find { it.processor == "fallback" }

                val bothHealthyRecently = list.size >= 2 &&
                        default!!.state == HealthCheckState.HEALTHY &&
                        fallback!!.state == HealthCheckState.HEALTHY &&
                        default.lastCheckedAt.isAfter(now.minusSeconds(touchThresholdSeconds)) &&
                        fallback.lastCheckedAt.isAfter(now.minusSeconds(touchThresholdSeconds))

                if (bothHealthyRecently) {
                    // cheap touch: update lastCheckedAt/expireAt but avoid remote call
                    val touchTime = Instant.now()
                    val touchOps = listOf("default", "fallback").map { processor ->
                        val q = Query.query(Criteria.where("_id").`is`(processor))
                        val u = Update()
                            .set("lastCheckedAt", touchTime)
                            .set("checkedBy", instanceId)
                            .set("expireAt", touchTime.plusSeconds(30))
                        mongoTemplate.upsert(q, u, "processor_health")
                    }
                    Mono.when(touchOps)
                        .then()
                } else {
                    // run a full check (concurrent calls, then upsert results)
                    Mono.when(fullCheck("default", defaultClient), fullCheck("fallback", fallbackClient)).then()
                }
            }
    }

    private fun fullCheck(processor: String, client: WebClient): Mono<Void> {
        val start = Instant.now()
        return client.get()
            .uri("/payments/service-health")
            .retrieve()
            .bodyToMono(ProcessorHealthResponse::class.java)
            .timeout(Duration.ofSeconds(requestTimeoutSeconds))
            .map { resp ->
                val healthy = resp.failing == false
                val latency = resp.minResponseTime.toLong()
                Triple(processor, healthy, latency)
            }
            .onErrorResume { e ->
                logger.warn(e) { "Health call failed for $processor" }
                Mono.just(Triple(processor, false, Long.MAX_VALUE))
            }
            .flatMap { (proc, healthy, latency) ->
                val now = Instant.now()
                val q = Query.query(Criteria.where("_id").`is`(proc))
                val u = Update()
                    .set("state", if (healthy) HealthCheckState.HEALTHY.name else HealthCheckState.UNHEALTHY.name)
                    .set("latencyMs", if (latency == Long.MAX_VALUE) null else latency)
                    .set("lastCheckedAt", now)
                    .set("checkedBy", instanceId)
                    .set("expireAt", now.plusSeconds(30))
                mongoTemplate.upsert(q, u, "processor_health").then()
            }
    }

    /**
     * Leader election: try to acquire a single document 'health-check-leader' with expireAt < now
     * Similar approach to prior implementation (findAndModify upsert).
     */
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
            .map { updated -> updated.owner == instanceId }
            .defaultIfEmpty(false)
            .onErrorReturn(false)
    }

    // DTO read from /payments/service-health
    private data class ProcessorHealthResponse(val failing: Boolean?, val minResponseTime: Int)
}
