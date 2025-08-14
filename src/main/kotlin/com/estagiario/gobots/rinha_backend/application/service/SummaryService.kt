package com.estagiario.gobots.rinha_backend.application.service

import com.estagiario.gobots.rinha_backend.infrastructure.incoming.dto.PaymentSummaryResponse
import reactor.core.publisher.Mono
import java.time.Instant

interface SummaryService {
    // ✅ Renamed to match your implementation's intent
    fun getPaymentsSummary(from: Instant, to: Instant): Mono<PaymentSummaryResponse>
}