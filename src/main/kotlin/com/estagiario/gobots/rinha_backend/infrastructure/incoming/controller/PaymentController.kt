// ATUALIZE ESTE FICHEIRO:
// src/main/kotlin/com/estagiario/gobots/rinha_backend/infrastructure/incoming/controller/PaymentController.kt

package com.estagiario.gobots.rinha_backend.infrastructure.incoming.controller

import com.estagiario.gobots.rinha_backend.application.service.PaymentService
import com.estagiario.gobots.rinha_backend.domain.Payment
import com.estagiario.gobots.rinha_backend.infrastructure.incoming.dto.PaymentRequest
import com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository.PaymentRepository
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.time.Instant

@RestController
@RequestMapping("/payments")
class PaymentController(
    private val paymentService: PaymentService,
    private val paymentRepository: PaymentRepository
) {

    private val logger = KotlinLogging.logger {}

    @PostMapping
    fun createPayment(@RequestBody request: PaymentRequest): Mono<ResponseEntity<Any>> {
        logger.info { "message=\"Requisição recebida\" correlationId=${request.correlationId} amount=${request.getFormattedAmount()}" }

        return paymentService.processNewPayment(request)
            .doOnSuccess { logger.info { "🎯 SERVICE RETORNOU SUCESSO para ${request.correlationId}" } }
            .then(Mono.just(ResponseEntity.accepted().build<Any>()))
            .doOnError { logger.error(it) { "🎯 CONTROLLER ERRO: ${it.message}" } }
            .onErrorReturn(ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).build())
    }

    @PostMapping("/test")
    fun testMongoPersistence(): Mono<ResponseEntity<String>> {
        logger.info { "🧪 Executando teste de persistência 100% reativo..." }

        // ✅ CORREÇÃO 1: Usando o seu factory method, como você sugeriu. O código fica mais limpo.
        val testPayment = Payment.newPayment(
            correlationId = "test-${Instant.now().toEpochMilli()}",
            amount = 9999L
        )

        // ✅ CORREÇÃO 2: Este fluxo reativo garante a subscrição e a execução.
        return paymentRepository.save(testPayment)
            .map { savedPayment ->
                // Este bloco SÓ é executado se o save for bem-sucedido.
                val successMsg = "✅ SUCESSO: Documento persistido com ID: ${savedPayment.correlationId}"
                logger.info { successMsg }
                ResponseEntity.status(201).body(successMsg)
            }
            .doOnError { error ->
                logger.error(error) { "❌ ERRO AO SALVAR: ${error.message}" }
            }
            .onErrorResume { error ->
                val errorMsg = "❌ FALHA: Erro ao persistir. Causa: ${error.message}"
                Mono.just(ResponseEntity.status(500).body(errorMsg))
            }
    }
}