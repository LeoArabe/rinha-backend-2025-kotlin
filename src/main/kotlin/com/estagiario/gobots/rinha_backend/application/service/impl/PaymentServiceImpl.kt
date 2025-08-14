package com.estagiario.gobots.rinha_backend.application.service.impl

import com.estagiario.gobots.rinha_backend.application.dto.HealthCheckResponse
import com.estagiario.gobots.rinha_backend.application.dto.PaymentAck
import com.estagiario.gobots.rinha_backend.application.dto.PaymentRequest
import com.estagiario.gobots.rinha_backend.application.service.PaymentService
import com.estagiario.gobots.rinha_backend.domain.HealthCheckState
import com.estagiario.gobots.rinha_backend.domain.Payment
import com.estagiario.gobots.rinha_backend.domain.PaymentEvent
import com.estagiario.gobots.rinha_backend.domain.exception.PaymentProcessingException
import com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository.PaymentEventRepository
import com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository.PaymentRepository
import com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository.ProcessorHealthRepository
import mu.KotlinLogging
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.math.RoundingMode

private val logger = KotlinLogging.logger {}

@Service
class PaymentServiceImpl(
    private val paymentRepo: PaymentRepository,
    private val eventRepo: PaymentEventRepository,
    private val processorHealthRepo: ProcessorHealthRepository,
    private val mongoTemplate: ReactiveMongoTemplate
) : PaymentService {

    override fun processNewPayment(request: PaymentRequest): Mono<PaymentAck> {
        // BigDecimal -> centavos com HALF_UP
        val amountCents = request.amount
            .movePointRight(2)
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()

        val payment = Payment.newPayment(
            correlationId = request.correlationId.toString(),
            amountInCents = amountCents
        )
        val event = PaymentEvent.newProcessPaymentEvent(payment.correlationId)

        return Mono.when(
            paymentRepo.save(payment)
                .onErrorResume(DuplicateKeyException::class.java) { Mono.empty() },
        eventRepo.save(event)
            .onErrorResume(DuplicateKeyException::class.java) { Mono.empty() }
        )
        .onErrorResume(DuplicateKeyException::class.java) { Mono.empty() }
            .onErrorMap { t ->
                logger.error(t) { "Erro ao persistir pagamento/evento: ${t.message}" }
                PaymentProcessingException("Falha ao processar pagamento", t)
            }
            .thenReturn(PaymentAck(request.correlationId))
    }

    override fun performHealthCheck(): Mono<HealthCheckResponse> {
        return processorHealthRepo.findAll()
            .collectMap(
                { it.processor },
                { p ->
                    mapOf(
                        "state" to p.state.name,
                        "latencyMs" to p.latencyMs,
                        "lastCheckedAt" to p.lastCheckedAt,
                        "checkedBy" to p.checkedBy
                    )
                }
            )
            .map { map ->
                val healthy = map.values.all { (it["state"] as? String) == HealthCheckState.HEALTHY.name }
                HealthCheckResponse(
                    isHealthy = healthy,
                    details = mapOf("processors" to map, "status" to if (healthy) "healthy" else "degraded")
                )
            }
            .defaultIfEmpty(
                HealthCheckResponse(
                    isHealthy = true,
                    details = mapOf("status" to "no_data", "processors" to emptyMap<String, Any>())
                )
            )
    }

    override fun testPersistence(): Mono<Void> =
        mongoTemplate.execute("test") { coll ->
            coll.insertOne(org.bson.Document("test", true))
        }.then()

    override fun getPaymentStatus(correlationId: String): Mono<Payment> =
        paymentRepo.findByCorrelationId(correlationId)
}
