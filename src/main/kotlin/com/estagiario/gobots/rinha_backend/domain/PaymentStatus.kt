package com.estagiario.gobots.rinha_backend.domain

enum class PaymentStatus {
    RECEBIDO,
    PROCESSANDO,
    SUCESSO,
    FALHA,
    AGENDADO_RETRY;

    fun isFinal(): Boolean = this == SUCESSO || this == FALHA
}