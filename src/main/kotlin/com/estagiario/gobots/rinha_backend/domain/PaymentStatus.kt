package com.estagiario.gobots.rinha_backend.domain

/**
 * Estados possíveis de um pagamento durante seu ciclo de vida.
 */
enum class PaymentStatus {
    RECEBIDO,       // Intenção de pagamento recebida e persistida
    PROCESSANDO,    // Worker pegou o pagamento para processar
    SUCESSO,        // Processado com sucesso (estado final)
    FALHA,          // Falhou permanentemente (estado final)
    AGENDADO_RETRY; // Falha temporária, agendado para nova tentativa

    companion object {
        private val FINAL_STATES = setOf(SUCESSO, FALHA)
    }

    fun isFinal(): Boolean = this in FINAL_STATES
}