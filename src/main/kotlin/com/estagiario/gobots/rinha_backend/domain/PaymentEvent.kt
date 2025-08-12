package com.estagiario.gobots.rinha_backend.domain

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

enum class PaymentEventStatus {
    PENDING,
    PROCESSING,
    PROCESSED
}

@Document("payment_events")
data class PaymentEvent(
    @Id
    val id: String? = null,
    val correlationId: String,
    val status: PaymentEventStatus,
    val createdAt: Instant,
    val owner: String? = null,
    val processingAt: Instant? = null,
    val processedAt: Instant? = null,
    val nextRetryAt: Instant? = null // ✅ CAMPO ADICIONADO
) {
    companion object {
        fun newProcessPaymentEvent(correlationId: String): PaymentEvent {
            return PaymentEvent(
                correlationId = correlationId,
                status = PaymentEventStatus.PENDING,
                createdAt = Instant.now()
            )
        }
    }
}