package com.estagiario.gobots.rinha_backend.infrastructure.incoming.controller

import com.estagiario.gobots.rinha_backend.application.service.PaymentService
import com.estagiario.gobots.rinha_backend.domain.exception.PaymentProcessingException
import com.estagiario.gobots.rinha_backend.infrastructure.incoming.dto.PaymentRequest
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
class PaymentController(private val paymentService: PaymentService) {

    @PostMapping
    fun createPayment(@Valid @RequestBody request: PaymentRequest): Mono<ResponseEntity<Map<String, String>>> {
        val correlationId = UUID.randomUUID().toString()
        val requestWithId = request.copy(correlationId = correlationId)

        return paymentService.processNewPayment(requestWithId)
            .map {
                ResponseEntity.accepted().body(mapOf("correlationId" to correlationId))
            }
            .doOnSuccess {
                logger.info { "Payment accepted successfully, correlationId=$correlationId" }
            }
            .onErrorResume { error ->
                when (error) {
                    is PaymentProcessingException -> {
                        logger.error(error) { "Error processing payment, correlationId=$correlationId" }
                        Mono.just(ResponseEntity.internalServerError().build())
                    }
                    else -> {
                        logger.error(error) { "Unexpected error, correlationId=$correlationId" }
                        Mono.just(ResponseEntity.badRequest().build())
                    }
                }
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
                    createdAt = payment.requestedAt, // Campo que faltava
                    processor = payment.processorUsed,
                    errorMessage = payment.lastErrorMessage
                )
                ResponseEntity.ok(response)
            }
            .defaultIfEmpty(ResponseEntity.notFound().build())
    }
}