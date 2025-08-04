// ATUALIZE ESTE FICHEIRO:
// src/main/kotlin/com/estagiario/gobots/rinha_backend/infrastructure/incoming/controller/PaymentController.kt

package com.estagiario.gobots.rinha_backend.infrastructure.incoming.controller

import com.estagiario.gobots.rinha_backend.application.service.PaymentService
import com.estagiario.gobots.rinha_backend.domain.Payment
import com.estagiario.gobots.rinha_backend.domain.PaymentStatus
import com.estagiario.gobots.rinha_backend.infrastructure.incoming.dto.PaymentRequest
import com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository.PaymentRepository
import jakarta.validation.Valid
import mu.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.Instant
import java.lang.Void // Importação explícita para o tipo Void do Java

@RestController
@RequestMapping("/payments")
class PaymentController(
    private val paymentService: PaymentService,
    private val paymentRepository: PaymentRepository
) {

    private val logger = KotlinLogging.logger {}

    @PostMapping
    fun createPayment(@Valid @RequestBody request: PaymentRequest): Mono<ResponseEntity<Void>> {
        return paymentService.processNewPayment(request)
            .thenReturn(ResponseEntity.accepted().build<Void>())
    }

    // ✅ ENDPOINT DE TESTE REESCRITO PARA SER 100% REATIVO
    @PostMapping("/test")
    fun testMongoPersistence(): Mono<ResponseEntity<String>> {
        logger.info { "🧪 Executando teste de persistência 100% reativo..." }

        val testPayment = Payment(
            correlationId = "test-${Instant.now().toEpochMilli()}", // ID único para cada teste
            amount = BigDecimal.valueOf(99.99),
            status = PaymentStatus.RECEBIDO,
            requestedAt = Instant.now(),
            lastUpdatedAt = Instant.now()
        )

        // Este fluxo reativo garante a subscrição e execução
        return paymentRepository.save(testPayment)
            .map { savedPayment ->
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