package com.estagiario.gobots.rinha_backend.infrastructure.client

class ProcessorPaymentException(
    message: String,
    val httpStatusCode: Int,
    cause: Throwable? = null
) : RuntimeException(message, cause) {

    /**
     * Determina se o erro é passível de retry baseado no código HTTP
     */
    fun isRetryable(): Boolean {
        return when (httpStatusCode) {
            // Erros de servidor (5xx) são passíveis de retry
            in 500..599 -> true
            // Timeout e alguns erros de cliente específicos
            408, 429, 503, 504 -> true
            // Demais erros não são passíveis de retry
            else -> false
        }
    }
}