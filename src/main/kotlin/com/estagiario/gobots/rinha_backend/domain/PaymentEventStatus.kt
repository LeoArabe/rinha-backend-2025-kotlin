package com.estagiario.gobots.rinha_backend.domain

/**
 * Representa o ciclo de vida de um evento no Outbox.
 */
enum class PaymentEventStatus {
    PENDING,
    PROCESSING,
    PROCESSED
}