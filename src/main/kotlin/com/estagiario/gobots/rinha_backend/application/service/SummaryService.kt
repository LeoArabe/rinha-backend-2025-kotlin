package com.estagiario.gobots.rinha_backend.application.service

import com.estagiario.gobots.rinha_backend.application.dto.PaymentsSummary
import reactor.core.publisher.Mono
import java.time.Instant

interface SummaryService {
    fun compute(from: Instant?, to: Instant?): Mono<PaymentsSummary>
}
