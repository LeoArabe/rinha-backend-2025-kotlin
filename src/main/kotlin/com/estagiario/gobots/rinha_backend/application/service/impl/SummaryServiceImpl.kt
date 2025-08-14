package com.estagiario.gobots.rinha_backend.application.service.impl

import com.estagiario.gobots.rinha_backend.application.service.SummaryService
import com.estagiario.gobots.rinha_backend.domain.PaymentStatus
import com.estagiario.gobots.rinha_backend.infrastructure.incoming.dto.PaymentSummaryResponse
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.time.Instant

@Service
class SummaryServiceImpl(
    private val template: ReactiveMongoTemplate
) : SummaryService {

    override fun getPaymentsSummary(from: Instant, to: Instant): Mono<PaymentSummaryResponse> {
        val criteria = Criteria.where("status").`is`(PaymentStatus.SUCCESS.name)
            .and("lastUpdatedAt").gte(from).lte(to)

        val aggregation = Aggregation.newAggregation(
            Aggregation.match(criteria),
            Aggregation.group("processorUsed")
                .sum("amount").`as`("totalAmount")
                .count().`as`("totalRequests")
        )

        return template.aggregate(aggregation, "payments", Map::class.java)
            .collectList()
            // ✅ A lógica de mapeamento foi movida para o DTO, limpando o serviço
            .map { results -> PaymentSummaryResponse.fromAggregation(results) }
    }
}