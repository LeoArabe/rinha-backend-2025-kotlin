package com.estagiario.gobots.rinha_backend.infrastructure.incoming.controller

import com.estagiario.gobots.rinha_backend.application.dto.PaymentsSummary // Corrigido para DTO da aplicação
import com.estagiario.gobots.rinha_backend.application.service.SummaryService
import com.estagiario.gobots.rinha_backend.domain.exception.InvalidDateRangeException
import com.estagiario.gobots.rinha_backend.infrastructure.incoming.dto.PaymentSummaryResponse
import com.estagiario.gobots.rinha_backend.infrastructure.incoming.dto.ProcessorSummary
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
        return summaryService.compute(from, to)
            .map { summary: PaymentsSummary -> // Adicionada tipagem explícita
                ResponseEntity.ok(
                    // Mapeamento do DTO de domínio para o DTO de infra
                    PaymentSummaryResponse(
                        defaultProcessor = ProcessorSummary(
                            totalRequests = summary.default.totalRequests,
                            totalAmount = summary.default.totalAmount
                        ),
                        fallbackProcessor = ProcessorSummary(
                            totalRequests = summary.fallback.totalRequests,
                            totalAmount = summary.fallback.totalAmount
                        )
                    )
                )
            }
            .onErrorResume { error: Throwable -> // Adicionada tipagem explícita
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