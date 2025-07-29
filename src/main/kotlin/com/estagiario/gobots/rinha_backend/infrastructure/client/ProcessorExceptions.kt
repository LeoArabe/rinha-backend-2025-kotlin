package com.estagiario.gobots.rinha_backend.infrastructure.client

/**
 * Exceção customizada para falhas no processamento de pagamentos nos serviços externos.
 * Contém o código de status HTTP para permitir uma lógica de retry inteligente.
 */
class ProcessorPaymentException(
    override val message: String,
    val httpStatusCode: Int,
    override val cause: Throwable? = null
) : RuntimeException(message, cause) {

    /**
     * Verifica se o erro é temporário (pode ser retentado).
     * Erros 4xx são de negócio (permanentes), enquanto erros 5xx e de rede/timeout (código 0) são temporários.
     */
    fun isRetryable(): Boolean {
        return httpStatusCode == 0 || httpStatusCode >= 500
    }
}