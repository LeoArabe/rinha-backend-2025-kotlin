package com.estagiario.gobots.rinha_backend.application.service.impl

import com.estagiario.gobots.rinha_backend.application.service.HealthStatus
import com.estagiario.gobots.rinha_backend.application.service.PaymentService
import com.estagiario.gobots.rinha_backend.domain.Payment
import com.estagiario.gobots.rinha_backend.domain.PaymentEvent
import com.estagiario.gobots.rinha_backend.infrastructure.incoming.dto.PaymentRequest
import com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository.PaymentEventRepository
import com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository.PaymentRepository
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.time.Instant

private val logger = KotlinLogging.logger {}

@Service
class PaymentServiceImpl(
    private val paymentRepository: PaymentRepository,
    private val paymentEventRepository: PaymentEventRepository,
    @Value("\${app.fail-on-persist-error:false}") private val failOnPersistError: Boolean
) : PaymentService {

    override fun processNewPayment(request: PaymentRequest): Mono<Void> {
        val payment = request.toDomainEntity()
        val event = PaymentEvent.newProcessPaymentEvent(payment.correlationId)

        return paymentRepository.save(payment)
            .flatMap { paymentEventRepository.save(event) }
            .doOnSuccess {
                logger.info { "message=\"Intenção de pagamento persistida\" correlationId=${request.correlationId}" }
            }
            .then()
            .onErrorResume { ex: Throwable ->
                logger.error(ex) { "Falha ao persistir intenção de pagamento: correlationId=${request.correlationId}" }
                if (failOnPersistError) Mono.error<Void>(ex) else Mono.empty()
            }
    }

    override fun getPaymentStatus(correlationId: String): Mono<Payment> {
        return paymentRepository.findByCorrelationId(correlationId)
    }

    override fun performHealthCheck(): Mono<HealthStatus> {
        return paymentRepository.count()
            .map { count -> HealthStatus(true, mapOf("database" to "UP", "payment_count" to count)) }
            .onErrorReturn(HealthStatus(false, mapOf("database" to "DOWN")))
    }

    override fun testPersistence(): Mono<Void> {
        val testPayment = Payment.newPayment("test-${Instant.now().toEpochMilli()}", 9999L)
        return paymentRepository.save(testPayment).then()
    }
}