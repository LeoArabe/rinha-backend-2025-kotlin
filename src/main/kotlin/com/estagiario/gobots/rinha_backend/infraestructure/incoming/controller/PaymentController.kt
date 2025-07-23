package com.estagiario.gobots.rinha_backend.infraestructure.incoming.controller

import com.estagiario.gobots.rinha_backend.infrastructure.incoming.dto.PaymentRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/payments")
class PaymentController(
    private val paymentService: PaymentService
) {
    @PostMapping
    suspend fun createPayment(
        @Valid @RequestBody request: PaymentRequest
    ): ResponseEntity<Void> {
        // Hot path absolutamente limpo - sem logs, sem operações desnecessárias
        paymentService.processNewPayment(request)

        // Retorna 202 Accepted sem body para máxima performance
        return ResponseEntity.accepted().build()
    }
}