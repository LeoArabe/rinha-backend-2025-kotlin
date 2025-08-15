package com.estagiario.gobots.rinha_backend.application.service.impl

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
import java.math.RoundingMode

private val logger = KotlinLogging.logger {}

@Service
class PaymentServiceImpl(
    private val paymentRepository: PaymentRepository,
    private val paymentEventRepository: PaymentEventRepository
) : PaymentService {

    override fun processNewPayment(request: PaymentRequest): Mono<Payment> {
        return Mono.fromCallable {
            val amountCents = request.amount
                .movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact()

            val payment = Payment.newPending(request.correlationId, amountCents)
            val event = PaymentEvent.newProcessPaymentEvent(request.correlationId)
            Pair(payment, event)
        }
            .flatMap { (payment, event) ->
                Mono.zip(
                    paymentRepository.save(payment),
                    paymentEventRepository.save(event)
                ).thenReturn(payment)
            }
            .doOnError { e: Throwable -> logger.error(e) { "Failed to create payment for ${request.correlationId}" } }
            .onErrorMap { e: Throwable -> PaymentProcessingException("Failed to create payment for ${request.correlationId}", e) }
    }

    override fun getPaymentStatus(correlationId: String): Mono<Payment> {
        return paymentRepository.findByCorrelationId(correlationId)
    }

    override fun updatePaymentStatus(payment: Payment, newStatus: PaymentStatus, processor: String?, error: String?): Mono<Payment> {
        val updatedPayment = when (newStatus) {
            PaymentStatus.SUCCESS -> payment.markAsSuccessful(processor ?: "unknown")
            PaymentStatus.FAILURE -> payment.markAsFailed(error)
            else -> payment.copy(status = newStatus)
        }
        return paymentRepository.save(updatedPayment)
    }
}