package com.estagiario.gobots.rinha_backend.infrastructure.client

/**
 * Exception customizada para falhas na verificação de saúde dos processadores.
 */
class ProcessorHealthCheckException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

/**
 * Exception customizada para falhas no processamento de pagamentos nos serviços externos.
 * Contém o código de status HTTP para permitir uma lógica de retry inteligente.
 */
class ProcessorPaymentException(
    message: String,
    val httpStatusCode: Int,
    cause: Throwable? = null
) : RuntimeException(message, cause) {

    /**
     * Verifica se o erro é temporário (pode ser retentado).
     * Erros 4xx são de negócio (permanentes), enquanto erros 5xx e de rede/timeout (código 0) são temporários.
     */
    fun isRetryable(): Boolean {
        return httpStatusCode == 0 || httpStatusCode >= 500
    }
}