package com.estagiario.gobots.rinha_backend.application.worker

import com.estagiario.gobots.rinha_backend.domain.PaymentEvent
import com.estagiario.gobots.rinha_backend.infrastructure.incoming.dto.PaymentRequest
import com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository.PaymentEventRepository
import com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository.PaymentRepository
import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronizationManager

@Component
class TransactionalPersistenceWorker(
    private val paymentRepository: PaymentRepository,
    private val paymentEventRepository: PaymentEventRepository
) {
    private val logger = KotlinLogging.logger {}

    @Transactional
    suspend fun savePaymentAndEvent(request: PaymentRequest) {
        val correlationId = request.correlationId

        // LOG ESTRATÉGICO 4: A PROVA FINAL
        // Este log é a "arma fumegante". Ele vai nos dizer se o Spring
        // realmente iniciou uma transação para este método.
        val isTransactionActive = TransactionSynchronizationManager.isActualTransactionActive()
        logger.info { "🔎 [ID: $correlationId] Verificando transação. Está ativa? $isTransactionActive" }

        if (!isTransactionActive) {
            logger.error { "🔥 [ID: $correlationId] ALERTA CRÍTICO: O método @Transactional foi chamado, MAS NÃO HÁ TRANSAÇÃO ATIVA. O contexto foi perdido!" }
        }

        val payment = request.toDomainEntity()
        paymentRepository.save(payment)

        val paymentEvent = PaymentEvent.newProcessPaymentEvent(payment.correlationId)
        paymentEventRepository.save(paymentEvent)

        // LOG ESTRATÉGICO 5: MUDANÇA DE SEMÂNTICA
        // Como você sugeriu, este log agora indica que o trabalho foi concluído
        // e a transação será marcada para commit.
        logger.info { "🎉 [ID: $correlationId] Bloco transacional concluído. Marcando para commit." }
    }
}