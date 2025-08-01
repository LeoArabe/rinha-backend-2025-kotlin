package com.estagiario.gobots.rinha_backend.application.worker

import com.estagiario.gobots.rinha_backend.domain.PaymentEvent
import com.estagiario.gobots.rinha_backend.infrastructure.incoming.dto.PaymentRequest
import com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository.PaymentEventRepository
import com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository.PaymentRepository
import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class TransactionalPersistenceWorker(
    private val paymentRepository: PaymentRepository,
    private val paymentEventRepository: PaymentEventRepository
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Este método é o coração da nossa estratégia de persistência.
     * * A anotação @Transactional aqui é ativada corretamente porque este
     * método é chamado a partir de um bean externo (o PaymentServiceImpl).
     * Isto garante que o Spring AOP crie um proxy transacional.
     *
     * As duas operações .save() dentro deste método são executadas de forma atómica:
     * ou ambas são bem-sucedidas (commit), ou ambas são desfeitas (rollback).
     */
    @Transactional
    suspend fun savePaymentAndEvent(request: PaymentRequest) {
        val payment = request.toDomainEntity()
        paymentRepository.save(payment)

        val paymentEvent = PaymentEvent.newProcessPaymentEvent(payment.correlationId)
        paymentEventRepository.save(paymentEvent)

        // Este log agora é 100% fiável. Se ele for impresso, a transação foi bem-sucedida.
        logger.info { "🎉 Transação para ${request.correlationId} commitada com sucesso!" }
    }
}