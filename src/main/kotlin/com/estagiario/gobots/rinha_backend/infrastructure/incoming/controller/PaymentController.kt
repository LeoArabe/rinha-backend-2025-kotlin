package com.estagiario.gobots.rinha_backend.infrastructure.incoming.controller

import com.estagiario.gobots.rinha_backend.application.service.PaymentService
import com.estagiario.gobots.rinha_backend.infrastructure.incoming.dto.PaymentRequest
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/payments")
class PaymentController(private val paymentService: PaymentService) {
    // ...
    @PostMapping
    fun createPayment(@Valid @RequestBody request: PaymentRequest): Mono<ResponseEntity<Void>> {
        return paymentService.processNewPayment(request)
            .thenReturn(ResponseEntity.accepted().build())
    }
}