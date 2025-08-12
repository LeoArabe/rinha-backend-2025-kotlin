package com.estagiario.gobots.rinha_backend.domain

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant
import java.util.UUID

@Document("payment_outbox")
@CompoundIndex(name = "status_created_at_idx", def = "{'status': 1, 'createdAt': 1}")
data class PaymentEvent(
    @Id
    val id: String = UUID.randomUUID().toString(),

    @Indexed
    val correlationId: String,

    val eventType: PaymentEventType,

    val createdAt: Instant = Instant.now(),

    var status: PaymentEventStatus = PaymentEventStatus.PENDING,

    // novo: owner para claim atômico
    var owner: String? = null,

    // novo: quando o worker marcou como em processamento
    var processingAt: Instant? = null,

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
    PROCESSING,
    PROCESSED
}
