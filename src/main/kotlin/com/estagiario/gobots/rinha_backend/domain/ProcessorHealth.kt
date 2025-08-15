package com.estagiario.gobots.rinha_backend.domain

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document("processor_health")
data class ProcessorHealth(
    @Id
    val processor: String,
    val state: HealthCheckState,
    val latencyMs: Long?,
    val lastCheckedAt: Instant,
    val checkedBy: String,
    val expireAt: Instant
) {
    companion object {
        fun newStatus(
            processor: String,
            state: HealthCheckState,
            latencyMs: Long?,
            checkedBy: String
        ): ProcessorHealth {
            val now = Instant.now()
            return ProcessorHealth(
                processor = processor,
                state = state,
                latencyMs = latencyMs,
                lastCheckedAt = now,
                checkedBy = checkedBy,
                expireAt = now.plusSeconds(60)
            )
        }
    }

    fun touch(instanceId: String): ProcessorHealth {
        val now = Instant.now()
        return this.copy(
            lastCheckedAt = now,
            checkedBy = instanceId,
            expireAt = now.plusSeconds(60)
        )
    }
}