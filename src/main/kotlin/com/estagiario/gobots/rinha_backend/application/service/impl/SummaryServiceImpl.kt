package com.estagiario.gobots.rinha_backend.application.service.impl

import com.estagiario.gobots.rinha_backend.application.dto.PaymentsSummary
import com.estagiario.gobots.rinha_backend.application.dto.SummaryPart
import com.estagiario.gobots.rinha_backend.application.service.SummaryService
import com.estagiario.gobots.rinha_backend.domain.PaymentStatus
import com.estagiario.gobots.rinha_backend.domain.exception.InvalidDateRangeException
import mu.KotlinLogging
import org.bson.Document
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation.*
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

private val logger = KotlinLogging.logger {}

@Service
class SummaryServiceOptimized(
    private val mongo: ReactiveMongoTemplate,
    @Value("\${app.summary.timeout-seconds:8}") private val timeoutSeconds: Long
) : SummaryService {

    override fun compute(from: Instant?, to: Instant?): Mono<PaymentsSummary> {
        // ⚡ Validação de entrada
        if (from != null && to != null && from.isAfter(to)) {
            return Mono.error(InvalidDateRangeException("Data 'from' deve ser anterior a 'to'"))
        }

        val criteria = buildCriteria(from, to)

        // ⚡ Pipeline MongoDB otimizada
        val pipeline = newAggregation(
            match(criteria),
            group("processorUsed")
                .count().`as`("totalRequests")
                .sum("amount").`as`("totalAmountCents")
        )

        return mongo.aggregate(pipeline, "payments", Document::class.java)
            .collectMap(
                { doc -> (doc.getString("_id") ?: "unknown").lowercase() },
                { doc ->
                    val requests = (doc.get("totalRequests") as Number?)?.toLong() ?: 0L
                    val amountCents = (doc.get("totalAmountCents") as Number?)?.toLong() ?: 0L
                    SummaryPart(
                        totalRequests = requests,
                        totalAmount = BigDecimal.valueOf(amountCents, 2) // ⚡ Direct conversion
                    )
                }
            )
            .map { resultsMap ->
                PaymentsSummary(
                    default = resultsMap["default"] ?: SummaryPart.empty(),
                    fallback = resultsMap["fallback"] ?: SummaryPart.empty()
                )
            }
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .doOnError { t ->
                logger.warn(t) { "Summary computation failed: ${t.message}" }
            }
            .onErrorReturn(PaymentsSummary(SummaryPart.empty(), SummaryPart.empty()))
    }

    private fun buildCriteria(from: Instant?, to: Instant?): Criteria {
        val criteriaList = mutableListOf<Criteria>()

        // ⚡ Filter only successful payments
        criteriaList.add(Criteria.where("status").`is`(PaymentStatus.SUCCESS.name))

        // ⚡ Date range filtering
        from?.let { criteriaList.add(Criteria.where("requestedAt").gte(it)) }
        to?.let { criteriaList.add(Criteria.where("requestedAt").lte(it)) }

        return if (criteriaList.size > 1) {
            Criteria().andOperator(*criteriaList.toTypedArray())
        } else {
            criteriaList.first()
        }
    }
}