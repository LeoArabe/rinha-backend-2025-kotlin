package com.estagiario.gobots.rinha_backend.application.service.impl

import com.estagiario.gobots.rinha_backend.application.dto.PaymentsSummary
import com.estagiario.gobots.rinha_backend.application.dto.SummaryPart
import com.estagiario.gobots.rinha_backend.application.service.SummaryService
import com.estagiario.gobots.rinha_backend.domain.PaymentStatus
import com.estagiario.gobots.rinha_backend.domain.exception.InvalidDateRangeException
import org.bson.Document
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.Instant

@Service
class SummaryServiceImpl(
    private val template: ReactiveMongoTemplate
) : SummaryService {

    override fun compute(from: Instant?, to: Instant?): Mono<PaymentsSummary> {
        if (from != null && to != null && from.isAfter(to)) {
            return Mono.error(InvalidDateRangeException("Data 'from' deve ser anterior a 'to'"))
        }

        val criteria = Criteria.where("status").`is`(PaymentStatus.SUCCESS.name)
        from?.let { criteria.and("lastUpdatedAt").gte(it) }
        to?.let { criteria.and("lastUpdatedAt").lte(it) }


        val aggregation = Aggregation.newAggregation(
            Aggregation.match(criteria),
            Aggregation.group("processorUsed")
                .sum("amount").`as`("totalAmount")
                .count().`as`("totalRequests")
        )

        return template.aggregate(aggregation, "payments", Document::class.java)
            .collectMap(
                { doc -> (doc.getString("_id") ?: "unknown").lowercase() },
                { doc ->
                    val requests = (doc.get("totalRequests") as Number?)?.toLong() ?: 0L
                    val amountCents = (doc.get("totalAmount") as Number?)?.toLong() ?: 0L
                    SummaryPart(
                        totalRequests = requests,
                        totalAmount = BigDecimal.valueOf(amountCents, 2)
                    )
                }
            )
            .map { resultsMap ->
                PaymentsSummary(
                    default = resultsMap["default"] ?: SummaryPart.empty(),
                    fallback = resultsMap["fallback"] ?: SummaryPart.empty()
                )
            }
    }
}