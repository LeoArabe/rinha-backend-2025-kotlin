package com.estagiario.gobots.rinha_backend.domain

enum class PaymentStatus {
    RECEIVED,
    PROCESSING,
    SUCCESS,
    FAILURE,
    RETRY_SCHEDULED;

    fun isFinal(): Boolean = this == SUCCESS || this == FAILURE
}