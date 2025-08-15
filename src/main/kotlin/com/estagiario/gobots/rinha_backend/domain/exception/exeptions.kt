package com.estagiario.gobots.rinha_backend.domain.exception

class PaymentProcessingException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class InvalidDateRangeException(message: String) : RuntimeException(message)