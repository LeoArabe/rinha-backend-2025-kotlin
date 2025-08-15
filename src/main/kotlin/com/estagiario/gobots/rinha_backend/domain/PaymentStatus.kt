package com.estagiario.gobots.rinha_backend.domain

/**
 * Representa o status de negócio da transação de pagamento.
 */
enum class PaymentStatus {
    RECEIVED,    // Recebido e aguardando na fila
    PROCESSING,  // Sendo ativamente processado pelo worker
    SUCCESS,     // Processamento concluído com sucesso
    FAILURE;     // Processamento concluído com falha

    fun isFinal(): Boolean = this == SUCCESS || this == FAILURE
}