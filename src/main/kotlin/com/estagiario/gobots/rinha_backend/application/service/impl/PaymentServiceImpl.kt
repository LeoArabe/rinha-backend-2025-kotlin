package com.estagiario.gobots.rinha_backend.application.service.impl

import com.estagiario.gobots.rinha_backend.application.service.HealthStatus
import com.estagiario.gobots.rinha_backend.application.service.PaymentService
import com.estagiario.gobots.rinha_backend.domain.Payment
import com.estagiario.gobots.rinha_backend.domain.PaymentEvent
import com.estagiario.gobots.rinha_backend.domain.PaymentStatus
import com.estagiario.gobots.rinha_backend.domain.exception.PaymentProcessingException
import com.estagiario.gobots.rinha_backend.infrastructure.incoming.dto.PaymentRequest
import com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository.PaymentEventRepository
import com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository.PaymentRepository
import mu.KotlinLogging
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

private val logger = KotlinLogging.logger {}

@Service
class PaymentServiceImpl(
    private val paymentRepository: PaymentRepository,
    private val paymentEventRepository: PaymentEventRepository
) : PaymentService {

    override fun processNewPayment(request: PaymentRequest): Mono<Void> {
        return try {
            val amountCents = request.amount
                .movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact()

            val payment = Payment.newPending(request.correlationId, amountCents)
            val event = PaymentEvent.newProcessPaymentEvent(request.correlationId)

            Mono.zip(
                paymentRepository.save(payment),
                paymentEventRepository.save(event)
            ).then()
        } catch (e: Exception) {
            Mono.error(PaymentProcessingException("Failed to create payment for ${request.correlationId}", e))
        }
    }

    override fun updatePaymentStatus(payment: Payment, newStatus: PaymentStatus, processor: String?, error: String?): Mono<Payment> {
        val updatedPayment = when (newStatus) {
            PaymentStatus.SUCCESS -> payment.markAsSuccessful(processor ?: "unknown")
            PaymentStatus.FAILURE -> payment.markAsFailed(error, processor ?: "none")
            else -> payment.copy(status = newStatus)
        }
        return paymentRepository.save(updatedPayment)
    }

    override fun getPaymentStatus(correlationId: String): Mono<Payment> {
        return paymentRepository.findByCorrelationId(correlationId)
    }

    override fun performHealthCheck(): Mono<HealthStatus> {
        return paymentRepository.count() // Exemplo: checa se consegue fazer uma contagem no banco
            .map { HealthStatus(true, mapOf("database_status" to "ok", "payment_count" to it)) }
            .onErrorResume { Mono.just(HealthStatus(false, mapOf("database_status" to "error"))) }
    }

    override fun testPersistence(): Mono<Void> {
        val testPayment = Payment.newPending("test-${Instant.now()}", 1L)
        return paymentRepository.save(testPayment).then()
    }
}