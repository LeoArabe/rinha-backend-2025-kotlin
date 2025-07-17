package com.estagiario.gobots.rinha_backend.infraestructure.incoming

import com.estagiario.gobots.rinha_backend.application.PaymentsService
import com.estagiario.gobots.rinha_backend.domain.model.PaymentsRequest
import com.estagiario.gobots.rinha_backend.domain.model.PaymentsResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping
class PaymentsController(
    private val service: PaymentsService
) {
    @PostMapping("/payments")
    suspend fun paymentsProcess(
        @RequestBody paymentsRequest: PaymentsRequest
    ) : ResponseEntity<PaymentsResponse> {
        return service.process(paymentsRequest)
    }

    @GetMapping("/payments/service-health")
    suspend fun healthCheck(){}

    @GetMapping("/payments-summary")
    suspend fun paymentsSumary(){}

    @GetMapping("/payments/{id}")
    suspend fun paymentsDetails(){}

    @GetMapping("/admin/payments-summary")
    suspend fun paymentsSummary(){}

    @PutMapping("/admin/configurations/token")
    suspend fun setToken(){}

    @PutMapping("/admin/configurations/delay")
    suspend fun setDelay(){}

    @PutMapping("/admin/configurations/failure")
    suspend fun setFailure(){}

    @PostMapping("/admin/purge-payments")
    suspend fun databasePurge(){}


}