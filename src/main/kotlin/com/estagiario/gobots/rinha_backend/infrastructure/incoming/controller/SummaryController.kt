package com.estagiario.gobots.rinha_backend.infrastructure.incoming.controller

import com.estagiario.gobots.rinha_backend.application.service.SummaryService
import com.estagiario.gobots.rinha_backend.infrastructure.incoming.dto.PaymentSummaryResponse
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/payments-summary")
class SummaryController(
    private val summaryService: SummaryService
) {
    @GetMapping
    suspend fun getPaymentsSummary(
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        from: Instant?,

        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        to: Instant?
    ): ResponseEntity<PaymentSummaryResponse> {
        val summary = summaryService.getSummary(from, to)
        return ResponseEntity.ok(summary)
    }
}