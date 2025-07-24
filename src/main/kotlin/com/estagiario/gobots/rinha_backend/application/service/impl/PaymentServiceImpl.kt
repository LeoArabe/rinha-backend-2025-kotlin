package com.estagiario.gobots.rinha_backend.application.service.impl

import com.estagiario.gobots.rinha_backend.application.service.PaymentService
import com.estagiario.gobots.rinha_backend.domain.PaymentEvent
import com.estagiario.gobots.rinha_backend.infrastructure.incoming.dto.PaymentRequest
import com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository.PaymentEventRepository
import com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository.PaymentRepository
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.reactor.mono
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.ReactiveTransaction
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait

@Service
class PaymentServiceImpl(
    private val paymentRepository: PaymentRepository,
    private val paymentEventRepository: PaymentEventRepository,
    private val transactionalOperator: TransactionalOperator
) : PaymentService {

    override suspend fun processNewPayment(request: PaymentRequest) {
        try {
            transactionalOperator.executeAndAwait {
                val payment = request.toDomainEntity()
                // CORREÇÃO: Dentro do transactionalOperator, o save() retorna Mono,
                // então precisamos usar awaitSingleOrNull() para esperar o resultado.
                paymentRepository.save(payment).awaitSingleOrNull()

                val paymentEvent = PaymentEvent.newProcessPaymentEvent(payment.correlationId)
                paymentEventRepository.save(paymentEvent).awaitSingleOrNull()
            }
        } catch (e: Exception) {
            if (e is DuplicateKeyException) {
                throw e
            }
            throw PaymentProcessingException("Falha ao persistir intenção de pagamento ${request.correlationId}", e)
        }
    }
}

// Extension function para facilitar o uso de transações com coroutines
suspend inline fun <T> TransactionalOperator.executeAndAwait(
    crossinline action: suspend (ReactiveTransaction) -> T?
): T? = execute { trx -> mono { action(trx) } }.awaitSingleOrNull()

// Exceção customizada
class PaymentProcessingException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)