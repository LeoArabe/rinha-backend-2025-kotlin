package com.estagiario.gobots.rinha_backend.infrastructure.incoming.controller

import com.estagiario.gobots.rinha_backend.application.dto.PaymentRequest
import com.estagiario.gobots.rinha_backend.application.service.PaymentService
import com.estagiario.gobots.rinha_backend.domain.exception.PaymentNotFoundException
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
    fun createPayment(@Valid @RequestBody request: PaymentRequest): Mono<ResponseEntity<Map<String, String>>> {
        val correlationId = UUID.randomUUID()
        val reqWithId = request.copy(correlationId = correlationId)

        return paymentService.processNewPayment(reqWithId)
            .map { ResponseEntity.accepted().body(mapOf("correlationId" to correlationId.toString())) }
            .onErrorResume {
                logger.error(it) { "Erro ao criar pagamento" }
                Mono.just(ResponseEntity.internalServerError().build())
            }
    }

    @GetMapping("/{correlationId}/status")
    fun getPaymentStatus(@PathVariable correlationId: String): Mono<ResponseEntity<Any>> {
        return paymentService.getPaymentStatus(correlationId)
            .map { ResponseEntity.ok(it) }
            .switchIfEmpty(Mono.error(PaymentNotFoundException("Pagamento não encontrado")))
            .onErrorResume(PaymentNotFoundException::class.java) {
                Mono.just(ResponseEntity.notFound().build())
            }
    }
}
