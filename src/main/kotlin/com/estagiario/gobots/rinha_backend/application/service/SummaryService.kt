// ATUALIZE ESTE FICHEIRO:
// src/main/kotlin/com/estagiario/gobots/rinha_backend/application/service/SummaryService.kt
package com.estagiario.gobots.rinha_backend.application.service

import com.estagiario.gobots.rinha_backend.infrastructure.incoming.dto.PaymentSummaryResponse
import reactor.core.publisher.Mono // ✅ ADICIONE A IMPORTAÇÃO
import java.time.Instant

interface SummaryService {
    // ✅ MUDADO DE 'suspend fun' PARA RETORNAR Mono
    fun getSummary(from: Instant?, to: Instant?): Mono<PaymentSummaryResponse>
}