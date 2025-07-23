package com.estagiario.gobots.rinha_backend.application.service.impl

import com.estagiario.gobots.rinha_backend.application.service.PaymentService
import com.estagiario.gobots.rinha_backend.domain.PaymentEvent
import com.estagiario.gobots.rinha_backend.infrastructure.incoming.dto.PaymentRequest
import com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository.PaymentEventRepository
import com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository.PaymentRepository
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait

@Service
class PaymentServiceImpl(
    private val paymentRepository: PaymentRepository,
    private val paymentEventRepository: PaymentEventRepository,
    private val transactionalOperator: TransactionalOperator
) : PaymentService {

    override suspend fun processNewPayment(request: PaymentRequest) {
        // A DuplicateKeyException será lançada daqui e tratada pelo ControllerAdvice.
        // Outras exceções são encapsuladas para tratamento genérico de erro 500.
        try {
            transactionalOperator.executeAndAwait {
                val payment = request.toDomainEntity()
                paymentRepository.save(payment).awaitSingleOrNull()

                val paymentEvent = PaymentEvent.newProcessPaymentEvent(payment.correlationId)
                paymentEventRepository.save(paymentEvent).awaitSingleOrNull()
            }
        } catch (e: Exception) {
            if (e is org.springframework.dao.DuplicateKeyException) {
                throw e // Deixa a exceção de duplicidade subir para o handler global
            }
            throw PaymentProcessingException("Falha ao persistir intenção de pagamento ${request.correlationId}", e)
        }
    }
}

// Extension function para facilitar o uso de transações com coroutines
suspend inline fun <T> TransactionalOperator.executeAndAwait(
    crossinline action: suspend (org.springframework.transaction.ReactiveTransaction) -> T?
): T? = execute { trx -> kotlinx.coroutines.reactor.mono { action(trx) } }.awaitSingleOrNull()

// Exceção customizada
class PaymentProcessingException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)