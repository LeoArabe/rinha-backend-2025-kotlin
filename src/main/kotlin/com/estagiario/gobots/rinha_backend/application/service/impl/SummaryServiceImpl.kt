package com.estagiario.gobots.rinha_backend.application.service.impl

import com.estagiario.gobots.rinha_backend.application.service.SummaryService
import com.estagiario.gobots.rinha_backend.domain.PaymentStatus
import com.estagiario.gobots.rinha_backend.domain.exception.InvalidDateRangeException
import com.estagiario.gobots.rinha_backend.domain.exception.SummaryQueryException
import com.estagiario.gobots.rinha_backend.infrastructure.incoming.dto.PaymentSummaryResponse
import com.estagiario.gobots.rinha_backend.infrastructure.incoming.dto.ProcessorSummary
import mu.KotlinLogging
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.Instant

@Service
class SummaryServiceImpl(
    private val mongoTemplate: ReactiveMongoTemplate
) : SummaryService {

    private val logger = KotlinLogging.logger {}

    override fun getSummary(from: Instant?, to: Instant?): Mono<PaymentSummaryResponse> {
        return Mono.fromCallable { validateDateRange(from, to) }
            .then(Mono.defer {
                val aggregation = buildAggregationPipeline(from, to)

                mongoTemplate.aggregate(aggregation, "payments", AggregationResult::class.java)
                    .collectMap(
                        { it.processorUsed ?: "unknown" },
                        { it }
                    )
                    .map { summaryMap ->
                        val defaultTotalCents = summaryMap["default"]?.totalAmount ?: 0L
                        val fallbackTotalCents = summaryMap["fallback"]?.totalAmount ?: 0L

                        // ✅ AJUSTE FINAL: Usamos .setScale(2) para garantir duas casas decimais.
                        val defaultAmount = BigDecimal.valueOf(defaultTotalCents)
                            .divide(BigDecimal(100))
                            .setScale(2, java.math.RoundingMode.HALF_UP)

                        val fallbackAmount = BigDecimal.valueOf(fallbackTotalCents)
                            .divide(BigDecimal(100))
                            .setScale(2, java.math.RoundingMode.HALF_UP)

                        val defaultSummary = ProcessorSummary(
                            totalRequests = summaryMap["default"]?.totalRequests ?: 0L,
                            totalAmount = defaultAmount
                        )

                        val fallbackSummary = ProcessorSummary(
                            totalRequests = summaryMap["fallback"]?.totalRequests ?: 0L,
                            totalAmount = fallbackAmount
                        )

                        PaymentSummaryResponse(defaultSummary, fallbackSummary)
                    }
                    .defaultIfEmpty(PaymentSummaryResponse.empty())
            })
            .doOnError { e -> logger.error(e) { "Falha ao executar consulta de resumo: from=$from, to=$to" } }
            .onErrorMap { e -> SummaryQueryException("Falha ao calcular resumo de pagamentos", e) }
    }

    private fun validateDateRange(from: Instant?, to: Instant?) {
        if (from != null && to != null && from.isAfter(to)) {
            throw InvalidDateRangeException("Data inicial ($from) não pode ser posterior à data final ($to)")
        }
    }

    private fun buildAggregationPipeline(from: Instant?, to: Instant?): Aggregation {
        // Sua pipeline de agregação já está correta para somar os Longs
        val dateCriteria = if (from != null || to != null) {
            Criteria.where("lastUpdatedAt").apply {
                from?.let { gte(it) }
                to?.let { lte(it) }
            }
        } else {
            null
        }

        val criteria = Criteria.where("status").`is`(PaymentStatus.SUCESSO.name)
        if (dateCriteria != null) {
            criteria.andOperator(dateCriteria)
        }

        val matchStage = Aggregation.match(criteria)
        val groupStage = Aggregation.group("processorUsed")
            .count().`as`("totalRequests")
            .sum("amount").`as`("totalAmount")
        val projectStage = Aggregation.project()
            .and("_id").`as`("processorUsed")
            .and("totalRequests").`as`("totalRequests")
            .and("totalAmount").`as`("totalAmount")

        return Aggregation.newAggregation(matchStage, groupStage, projectStage)
    }

    private data class AggregationResult(
        val processorUsed: String?,
        val totalRequests: Long,
        val totalAmount: Long // O resultado do MongoDB vem em centavos (Long)
    )
}