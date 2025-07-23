package com.estagiario.gobots.rinha_backend.application.service

import com.estagiario.gobots.rinha_backend.infrastructure.incoming.dto.PaymentSummaryResponse
import java.time.Instant

interface SummaryService {
    suspend fun getSummary(from: Instant?, to: Instant?): PaymentSummaryResponse
}