package com.estagiario.gobots.rinha_backend.infrastructure.incoming.handler

import com.estagiario.gobots.rinha_backend.application.service.impl.InvalidDateRangeException
import com.estagiario.gobots.rinha_backend.application.service.impl.PaymentProcessingException
import mu.KotlinLogging
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.support.WebExchangeBindException

@ControllerAdvice
class GlobalExceptionHandler {

    private val logger = KotlinLogging.logger {}

    @ExceptionHandler(DuplicateKeyException::class)
    fun handleDuplicateKey(ex: DuplicateKeyException): ResponseEntity<ErrorResponse> {
        logger.info { "Tentativa de criar pagamento duplicado (idempotente): ${ex.message}" }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(
            ErrorResponse(
                code = "DUPLICATE_PAYMENT",
                message = "Requisição de pagamento já foi aceita anteriormente."
            )
        )
    }

    @ExceptionHandler(WebExchangeBindException::class)
    fun handleValidationException(ex: WebExchangeBindException): ResponseEntity<ErrorResponse> {
        val errors = ex.bindingResult.fieldErrors.joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
        logger.warn { "Falha na validação do request: $errors" }
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
            ErrorResponse(
                code = "VALIDATION_ERROR",
                message = "Dados da requisição inválidos: $errors"
            )
        )
    }

    @ExceptionHandler(InvalidDateRangeException::class)
    fun handleInvalidDateRange(ex: InvalidDateRangeException): ResponseEntity<ErrorResponse> {
        logger.warn { "Range de datas inválido: ${ex.message}" }
        return ResponseEntity.badRequest().body(
            ErrorResponse(
                code = "INVALID_DATE_RANGE",
                message = ex.message ?: "Período de consulta inválido."
            )
        )
    }

    @ExceptionHandler(PaymentProcessingException::class)
    fun handlePaymentProcessing(ex: PaymentProcessingException): ResponseEntity<ErrorResponse> {
        logger.error(ex) { "Falha crítica no processamento de pagamento: ${ex.message}" }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ErrorResponse(
                code = "PROCESSING_FAILURE",
                message = "Erro interno ao processar a intenção de pagamento."
            )
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception): ResponseEntity<ErrorResponse> {
        logger.error(ex) { "Erro interno não tratado: ${ex.message}" }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ErrorResponse(
                code = "INTERNAL_SERVER_ERROR",
                message = "Ocorreu um erro inesperado no servidor."
            )
        )
    }
}