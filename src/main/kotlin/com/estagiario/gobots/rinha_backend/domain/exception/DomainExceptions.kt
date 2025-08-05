package com.estagiario.gobots.rinha_backend.domain.exception

/**
 * Exceção para falhas genéricas no processamento de pagamentos.
 */
class PaymentProcessingException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Exceção para quando o período de datas na consulta do summary é inválido (ex: from > to).
 */
class InvalidDateRangeException(message: String) : RuntimeException(message)

/**
 * Exceção para quando uma consulta de resumo falha.
 */
class SummaryQueryException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)