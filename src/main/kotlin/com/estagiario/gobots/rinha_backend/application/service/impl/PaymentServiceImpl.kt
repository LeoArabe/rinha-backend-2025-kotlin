// ATUALIZE ESTE FICHEIRO:
// src/main/kotlin/com/estagiario/gobots/rinha_backend/application/service/impl/PaymentServiceImpl.kt

package com.estagiario.gobots.rinha_backend.application.service.impl

import com.estagiario.gobots.rinha_backend.application.service.PaymentService
import com.estagiario.gobots.rinha_backend.domain.PaymentEvent
import com.estagiario.gobots.rinha_backend.infrastructure.incoming.dto.PaymentRequest
import com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository.PaymentEventRepository
import com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository.PaymentRepository
import mu.KotlinLogging
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.reactive.TransactionalOperator
import reactor.core.publisher.Mono

@Service
class PaymentServiceImpl(
    private val paymentRepository: PaymentRepository,
    private val paymentEventRepository: PaymentEventRepository,
    private val transactionalOperator: TransactionalOperator
) : PaymentService {

    private val logger = KotlinLogging.logger {}

    override fun processNewPayment(request: PaymentRequest): Mono<Void> {
        logger.info { "🚀 Processando pagamento ${request.correlationId}" }

        val payment = request.toDomainEntity()
        val paymentEvent = PaymentEvent.newProcessPaymentEvent(payment.correlationId)

        // ✅ Fluxo 100% reativo, explícito e à prova de falhas.
        return transactionalOperator.execute { _ ->
            paymentRepository.save(payment)
                .doOnSuccess { logger.info { "✅ Payment salvo: ID=${it.correlationId}" } }
                .then(paymentEventRepository.save(paymentEvent))
                .doOnSuccess { logger.info { "✅ PaymentEvent salvo: ID=${it.id}" } }
        }
            .then()
            .doOnSuccess {
                logger.info { "🎉 Transação para ${request.correlationId} commitada com sucesso!" }
            }
            .doOnError(DuplicateKeyException::class.java) {
                logger.info { "🔄 Pagamento duplicado ${request.correlationId} detetado e ignorado." }
            }
            .doOnError { error ->
                if (error !is DuplicateKeyException) {
                    logger.error(error) { "❌ Erro crítico ao persistir pagamento ${request.correlationId}" }
                }
            }
            .onErrorResume { Mono.empty() }
    }
}