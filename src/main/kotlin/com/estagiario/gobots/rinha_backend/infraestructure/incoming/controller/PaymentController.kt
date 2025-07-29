// src/main/kotlin/com/estagiario/gobots/rinha_backend/infraestructure/incoming/controller/PaymentController.kt

package com.estagiario.gobots.rinha_backend.infraestructure.incoming.controller

import com.estagiario.gobots.rinha_backend.application.service.PaymentService // <-- ADICIONADO
import com.estagiario.gobots.rinha_backend.infrastructure.incoming.dto.PaymentRequest
import jakarta.validation.Valid // <-- ADICIONADO
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
        paymentService.processNewPayment(request)
        return ResponseEntity.accepted().build()
    }
}