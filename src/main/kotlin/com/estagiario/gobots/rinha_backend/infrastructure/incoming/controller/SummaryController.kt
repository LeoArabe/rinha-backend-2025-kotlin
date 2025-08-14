package com.estagiario.gobots.rinha_backend.infrastructure.incoming.controller

import com.estagiario.gobots.rinha_backend.application.service.SummaryService
import com.estagiario.gobots.rinha_backend.domain.exception.InvalidDateRangeException
import com.estagiario.gobots.rinha_backend.infrastructure.incoming.dto.PaymentSummaryResponse
import mu.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.time.Instant

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/payments-summary")
class SummaryController(
    private val summaryService: SummaryService
) {

    @GetMapping
    fun getPaymentsSummary(
        @RequestParam("from") from: Instant,
        @RequestParam("to") to: Instant
    ): Mono<ResponseEntity<PaymentSummaryResponse>> {
        return summaryService.getPaymentsSummary(from, to) // ✅ CORRIGIDO: usa o nome de método correto
            .map { summary -> // ✅ TIPO EXPLÍCITO
                ResponseEntity.ok(summary)
            }
            .onErrorResume { error: Throwable -> // ✅ TIPO EXPLÍCITO
                when (error) {
                    is InvalidDateRangeException -> {
                        logger.warn(error) { "Invalid date range provided" }
                        Mono.just(ResponseEntity.badRequest().build())
                    }
                    else -> {
                        logger.error(error) { "Failed to retrieve summary" }
                        Mono.just(ResponseEntity.internalServerError().build())
                    }
                }
            }
    }
}