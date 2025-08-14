package com.estagiario.gobots.rinha_backend.domain

/**
 * Representa o ciclo de vida de um evento no Outbox.
 * - PENDING: Aguardando processamento.
 * - PROCESSING: Sendo processado por um worker.
 * - PROCESSED: Processamento concluído (com sucesso ou falha).
 */
enum class PaymentEventStatus {
    PENDING,
    PROCESSING,
    PROCESSED
}