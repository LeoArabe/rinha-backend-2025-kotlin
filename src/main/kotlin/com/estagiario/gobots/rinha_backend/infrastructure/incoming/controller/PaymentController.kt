package com.estagiario.gobots.rinha_backend.infrastructure.incoming.controller

import com.estagiario.gobots.rinha_backend.application.service.PaymentService
import com.estagiario.gobots.rinha_backend.infrastructure.incoming.dto.PaymentRequest
import jakarta.validation.Valid
import mu.KotlinLogging
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono
import java.text.DecimalFormat
import java.util.Locale

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/payments")
class PaymentController(
    private val paymentService: PaymentService
) {

    @PostMapping
    fun createPayment(@Valid @RequestBody request: PaymentRequest): Mono<ResponseEntity<Any>> {
        logger.info {
            "message=\"Requisição recebida\" correlationId=${request.correlationId} " +
                    "amount=${request.getFormattedAmount()}"
        }

        if (!request.hasValidUuidFormat()) {
            logger.warn { "correlationId com formato inválido: ${request.correlationId}" }
            return Mono.just(
                ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(mapOf("code" to "INVALID_UUID", "message" to "correlationId deve ser um UUID válido") as Any)
            )
        }

        if (request.amount.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            logger.warn { "Valor inválido para pagamento: ${request.amount}" }
            return Mono.just(
                ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(mapOf("code" to "INVALID_AMOUNT", "message" to "O valor do pagamento deve ser maior que zero") as Any)
            )
        }

        return paymentService.processNewPayment(request)
            .then(Mono.just(ResponseEntity.accepted().build<Any>()))
            .doOnSuccess {
                logger.info { "Pagamento aceito com sucesso - correlationId=${request.correlationId}" }
            }
            .onErrorResume { ex ->
                when (ex) {
                    is DuplicateKeyException -> {
                        logger.info { "Tentativa de criar pagamento duplicado (comportamento idempotente): correlationId=${request.correlationId}" }
                        Mono.just(
                            ResponseEntity.status(HttpStatus.ACCEPTED)
                                .body(mapOf("code" to "DUPLICATE_PAYMENT", "message" to "Pagamento já foi aceito anteriormente") as Any)
                        )
                    }
                    else -> {
                        logger.error(ex) { "Erro ao processar pagamento - correlationId=${request.correlationId}: ${ex.message}" }
                        Mono.just(
                            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(mapOf("code" to "PROCESSING_ERROR", "message" to "Erro interno ao processar pagamento") as Any)
                        )
                    }
                }
            }
    }

    @GetMapping("/health")
    fun healthCheck(): Mono<ResponseEntity<Any>> {
        logger.debug { "Health check requisitado" }
        return paymentService.performHealthCheck()
            .map { healthStatus ->
                val status = if (healthStatus.isHealthy) HttpStatus.OK else HttpStatus.SERVICE_UNAVAILABLE
                ResponseEntity.status(status).body(mapOf<String, Any>(
                    "status" to if (healthStatus.isHealthy) "UP" else "DOWN",
                    "timestamp" to java.time.Instant.now().toString(),
                    "details" to healthStatus.details
                ) as Any)
            }
            .onErrorResume { error ->
                logger.error(error) { "Erro durante health check: ${error.message}" }
                Mono.just(
                    ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(mapOf<String, Any>(
                            "status" to "DOWN",
                            "timestamp" to java.time.Instant.now().toString(),
                            "error" to (error.message ?: "Erro desconhecido")
                        ) as Any)
                )
            }
    }

    @PostMapping("/test")
    fun testMongoPersistence(): Mono<ResponseEntity<String>> {
        logger.info { "🧪 Executando teste de persistência 100% reativo..." }
        return paymentService.testPersistence()
            .map { ResponseEntity.ok("Teste de persistência executado com sucesso") }
            .onErrorResume { error ->
                logger.error(error) { "Erro no teste de persistência: ${error.message}" }
                Mono.just(
                    ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Erro no teste de persistência: ${error.message}")
                )
            }
    }

    @GetMapping("/{correlationId}/status")
    fun getPaymentStatus(@PathVariable correlationId: String): Mono<ResponseEntity<Any>> {
        logger.debug { "Consultando status do pagamento: $correlationId" }
        try {
            java.util.UUID.fromString(correlationId)
        } catch (ex: IllegalArgumentException) {
            return Mono.just(
                ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(mapOf("code" to "INVALID_UUID", "message" to "correlationId deve ser um UUID válido") as Any)
            )
        }

        return paymentService.getPaymentStatus(correlationId)
            .map { payment ->
                val amountDecimal = payment.amount.toBigDecimal().divide(100.toBigDecimal())
                val formattedAmount = DecimalFormat.getCurrencyInstance(Locale.of("pt", "BR")).format(amountDecimal)

                ResponseEntity.ok(mapOf<String, Any>(
                    "correlationId" to payment.correlationId,
                    "status" to payment.status.name,
                    "amount" to formattedAmount,
                    "requestedAt" to payment.requestedAt,
                    "createdAt" to payment.createdAt, // propriedade computed
                    "lastUpdatedAt" to payment.lastUpdatedAt,
                    "processorUsed" to (payment.processorUsed ?: "N/A"),
                    "attemptCount" to payment.attemptCount
                ) as Any)
            }
            .switchIfEmpty(
                Mono.just(
                    ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(mapOf("code" to "PAYMENT_NOT_FOUND", "message" to "Pagamento não encontrado") as Any)
                )
            )
            .onErrorResume { error ->
                logger.error(error) { "Erro ao consultar status do pagamento $correlationId: ${error.message}" }
                Mono.just(
                    ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(mapOf("code" to "QUERY_ERROR", "message" to "Erro interno ao consultar pagamento") as Any)
                )
            }
    }
}