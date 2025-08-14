package com.estagiario.gobots.rinha_backend.domain

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant
/**
 * Exceção para falhas genéricas no processamento de pagamentos.
 */
class PaymentProcessingException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Exceção para quando o período de datas na consulta do summary é inválido (ex: from > to).
 */
class InvalidDateRangeException(message: String) : RuntimeException(message)

/**
 * Exceção para quando uma consulta de resumo falha.
 */
class SummaryQueryException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

@Document("processor_health")
data class ProcessorHealth(
    @Id
    val processor: String, // "default" | "fallback"
    val state: HealthCheckState,
    val latencyMs: Long? = null,
    val lastCheckedAt: Instant,
    val checkedBy: String? = null, // instance que fez o check
    val expireAt: Instant? = null  // TTL para MongoDB
) {
    companion object {
        fun healthy(processor: String, latency: Long?, checkedBy: String): ProcessorHealth {
            val now = Instant.now()
            return ProcessorHealth(
                processor = processor,
                state = HealthCheckState.HEALTHY,
                latencyMs = latency,
                lastCheckedAt = now,
                checkedBy = checkedBy,
                expireAt = now.plusSeconds(30) // TTL 30s
            )
        }

        fun unhealthy(processor: String, checkedBy: String, error: String? = null): ProcessorHealth {
            val now = Instant.now()
            return ProcessorHealth(
                processor = processor,
                state = HealthCheckState.UNHEALTHY,
                latencyMs = null,
                lastCheckedAt = now,
                checkedBy = checkedBy,
                expireAt = now.plusSeconds(30)
            )
        }
    }
}