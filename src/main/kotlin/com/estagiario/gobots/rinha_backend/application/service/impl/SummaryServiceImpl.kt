package com.estagiario.gobots.rinha_backend.application.service.impl

import com.estagiario.gobots.rinha_backend.application.service.SummaryService
import com.estagiario.gobots.rinha_backend.domain.PaymentStatus
import com.estagiario.gobots.rinha_backend.infrastructure.incoming.dto.PaymentSummaryResponse
import com.estagiario.gobots.rinha_backend.infrastructure.incoming.dto.ProcessorSummary
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.reactive.asFlow
import mu.KotlinLogging
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Instant

@Service
class SummaryServiceImpl(
    private val mongoTemplate: ReactiveMongoTemplate
) : SummaryService {

    private val logger = KotlinLogging.logger {}

    override suspend fun getSummary(from: Instant?, to: Instant?): PaymentSummaryResponse {
        validateDateRange(from, to)

        try {
            val aggregation = buildAggregationPipeline(from, to)

            val summaryMap = mongoTemplate
                .aggregate(aggregation, "payments", AggregationResult::class.java)
                .asFlow() // Converte para Flow para processamento em streaming
                .fold(mutableMapOf<String, ProcessorSummary>()) { acc, result ->
                    acc[result.processorUsed] = ProcessorSummary(result.totalRequests, result.totalAmount)
                    acc
                }

            val defaultSummary = summaryMap["default"] ?: ProcessorSummary.empty()
            val fallbackSummary = summaryMap["fallback"] ?: ProcessorSummary.empty()

            return PaymentSummaryResponse(defaultSummary, fallbackSummary)

        } catch (e: Exception) {
            logger.error(e) { "Falha ao executar consulta de resumo: from=$from, to=$to" }
            throw SummaryQueryException("Falha ao calcular resumo de pagamentos", e)
        }
    }

    private fun validateDateRange(from: Instant?, to: Instant?) {
        if (from != null && to != null && from.isAfter(to)) {
            throw InvalidDateRangeException("Data inicial ($from) não pode ser posterior à data final ($to)")
        }
    }

    private fun buildAggregationPipeline(from: Instant?, to: Instant?): Aggregation {
        val matchStage = Aggregation.match(
            Criteria.where("status").`is`(PaymentStatus.SUCESSO.name).apply {
                if (from != null || to != null) {
                    val dateCriteria = Criteria.where("lastUpdatedAt")
                    from?.let { dateCriteria.gte(it) }
                    to?.let { dateCriteria.lte(it) }
                    andOperator(dateCriteria)
                }
            }
        )

        val groupStage = Aggregation.group("processorUsed")
            .count().`as`("totalRequests")
            .sum("amount").`as`("totalAmount")

        val projectStage = Aggregation.project()
            .and("_id").`as`("processorUsed")
            .and("totalRequests").`as`("totalRequests")
            .and("totalAmount").`as`("totalAmount")

        return Aggregation.newAggregation(matchStage, groupStage, projectStage)
    }

    // DTO privado para mapear o resultado da agregação do MongoDB
    private data class AggregationResult(
        val processorUsed: String,
        val totalRequests: Long,
        val totalAmount: BigDecimal
    )
}

// Exceções customizadas
class SummaryQueryException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
class InvalidDateRangeException(message: String) : RuntimeException(message)