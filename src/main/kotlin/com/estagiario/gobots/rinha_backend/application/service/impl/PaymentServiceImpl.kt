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
            )
                .doOnSuccess {
                    logger.info { "message=\"Payment and Event persisted\" correlationId=${request.correlationId}" }
                }
                .doOnError { ex ->
                    logger.error(ex) { "message=\"Error persisting payment/event\" correlationId=${request.correlationId}" }
                }
                .then()
        } catch (e: ArithmeticException) {
            val msg = "Invalid amount format for correlationId=${request.correlationId}"
            logger.warn(e) { msg }
            Mono.error(PaymentProcessingException(msg, e))
        } catch (e: Exception) {
            val msg = "Failed to create payment for correlationId=${request.correlationId}"
            logger.error(e) { msg }
            Mono.error(PaymentProcessingException(msg, e))
        }
    }

    // ✅ CORREÇÃO: Adicionada a palavra-chave "override"
    override fun updatePaymentStatus(payment: Payment, newStatus: PaymentStatus, processor: String?, error: String?): Mono<Payment> {
        val updatedPayment = payment.copy(
            status = newStatus,
            processorUsed = processor,
            lastErrorMessage = error,
            lastUpdatedAt = Instant.now()
        )
        return paymentRepository.save(updatedPayment)
    }

    override fun performHealthCheck(): Mono<HealthStatus> {
        return Mono.just(HealthStatus(true, mapOf("database" to "ok")))
    }

    override fun getPaymentStatus(correlationId: String): Mono<Payment> {
        return paymentRepository.findByCorrelationId(correlationId)
    }

    override fun testPersistence(): Mono<Void> {
        return Mono.empty()
    }
}