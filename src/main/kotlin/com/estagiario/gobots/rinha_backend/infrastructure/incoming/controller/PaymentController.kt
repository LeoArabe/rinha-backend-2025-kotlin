package com.estagiario.gobots.rinha_backend.infrastructure.incoming.controller

import com.estagiario.gobots.rinha_backend.application.dto.PaymentRequest
import com.estagiario.gobots.rinha_backend.application.service.PaymentService
import com.estagiario.gobots.rinha_backend.domain.exception.PaymentProcessingException
import com.estagiario.gobots.rinha_backend.infrastructure.incoming.dto.PaymentRequest as IncomingPaymentRequest
import com.estagiario.gobots.rinha_backend.infrastructure.incoming.dto.PaymentStatusResponse
import jakarta.validation.Valid
import mu.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono
import java.util.*

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/payments")
class PaymentController(
    private val paymentService: PaymentService
) {

    @PostMapping
    fun createPayment(@Valid @RequestBody request: IncomingPaymentRequest): Mono<ResponseEntity<Map<String, String>>> {
        val correlationId = UUID.randomUUID()

        // Converte DTO de entrada para DTO de aplicação
        val appRequest = PaymentRequest(
            correlationId = correlationId,
            amount = request.amount
        )

        return paymentService.processNewPayment(appRequest)
            .map {
                ResponseEntity.accepted().body(mapOf("correlationId" to correlationId.toString()))
            }
            .doOnSuccess {
                logger.info { "message=\"Pagamento aceito com sucesso\" correlationId=$correlationId" }
            }
            .onErrorResume { error: Throwable ->
                when (error) {
                    is PaymentProcessingException -> {
                        logger.error(error) { "message=\"Erro ao processar pagamento\" correlationId=$correlationId" }
                        Mono.just(ResponseEntity.internalServerError().build())
                    }
                    else -> {
                        logger.error(error) { "message=\"Erro inesperado\" correlationId=$correlationId" }
                        Mono.just(ResponseEntity.badRequest().build())
                    }
                }
            }
    }

    @GetMapping("/health")
    fun healthCheck(): Mono<ResponseEntity<Any>> {
        return paymentService.performHealthCheck()
            .map { health ->
                if (health.isHealthy) {
                    ResponseEntity.ok().body<Any>(health.details)
                } else {
                    ResponseEntity.status(503).body<Any>(health.details)
                }
            }
            .onErrorResume { error: Throwable ->
                logger.error(error) { "message=\"Health check falhou gravemente\"" }
                Mono.just(ResponseEntity.status(500).body(mapOf("status" to "critical_error")))
            }
    }

    @PostMapping("/test")
    fun testMongoPersistence(): Mono<ResponseEntity<Void>> {
        return paymentService.testPersistence()
            .map { ResponseEntity.ok().build<Void>() }
            .onErrorResume { error: Throwable ->
                logger.error(error) { "message=\"Teste de persistência falhou\"" }
                Mono.just(ResponseEntity.internalServerError().build())
            }
    }

    @GetMapping("/{correlationId}/status")
    fun getPaymentStatus(@PathVariable correlationId: String): Mono<ResponseEntity<PaymentStatusResponse>> {
        return paymentService.getPaymentStatus(correlationId)
            .map { payment ->
                val response = PaymentStatusResponse(
                    correlationId = payment.correlationId,
                    status = payment.status.name,
                    amount = payment.amount,
                    createdAt = payment.createdAt,
                    processor = payment.processorUsed,
                    errorMessage = payment.lastErrorMessage
                )
                ResponseEntity.ok(response)
            }
            .defaultIfEmpty(ResponseEntity.notFound().build())
    }
}