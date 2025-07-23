package com.estagiario.gobots.rinha_backend.domain

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant
import java.util.UUID

/**
 * SIMPLIFICADO: Evento para o Outbox Pattern - apenas um "gatilho" descartável.
 */
@Document("payment_outbox")
data class PaymentEvent(
    @Id
    val id: String = UUID.randomUUID().toString(),

    @Indexed
    val correlationId: String,

    val eventType: PaymentEventType,

    val createdAt: Instant = Instant.now(),

    @Indexed
    var status: PaymentEventStatus = PaymentEventStatus.PENDING,

    var processedAt: Instant? = null
) {
    companion object {
        fun newProcessPaymentEvent(correlationId: String): PaymentEvent {
            return PaymentEvent(
                correlationId = correlationId,
                eventType = PaymentEventType.PROCESS_PAYMENT
            )
        }
    }
}

enum class PaymentEventType {
    PROCESS_PAYMENT
}

enum class PaymentEventStatus {
    PENDING,
    PROCESSED
}