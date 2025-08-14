package com.estagiario.gobots.rinha_backend.domain

/**
 * Represents the business status of the Payment transaction.
 */
enum class PaymentStatus {
    RECEIVED,    // Recebido e aguardando processamento na fila
    PROCESSING,  // Sendo ativamente processado pelo worker
    SUCCESS,     // Processamento concluído com sucesso
    FAILURE;     // Processamento concluído com falha

    fun isFinal(): Boolean = this == SUCCESS || this == FAILURE
}