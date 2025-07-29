// Caminho: src/main/kotlin/com/estagiario/gobots/rinha_backend/application/service/impl/PaymentServiceImpl.kt
package com.estagiario.gobots.rinha_backend.application.service.impl

import com.estagiario.gobots.rinha_backend.application.service.PaymentService
import com.estagiario.gobots.rinha_backend.domain.PaymentEvent
import com.estagiario.gobots.rinha_backend.infrastructure.incoming.dto.PaymentRequest
import com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository.PaymentEventRepository
import com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository.PaymentRepository
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.reactor.mono
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

    /**
     * VERSÃO CORRETA E FINAL: Sem try-catch, delega o tratamento de erros
     * para o GlobalExceptionHandler de forma centralizada.
     */
    override suspend fun processNewPayment(request: PaymentRequest) {
        transactionalOperator.executeAndAwait {
            val payment = request.toDomainEntity()
            paymentRepository.save(payment)

            val paymentEvent = PaymentEvent.newProcessPaymentEvent(payment.correlationId)
            paymentEventRepository.save(paymentEvent)
        }
    }
}

/**
 * Extension function corrigida para trabalhar com Flux -> Mono -> awaitSingleOrNull.
 * O .next() é a chave para converter o Flux<T> do 'execute' em um Mono<T>.
 */
private suspend inline fun <T> TransactionalOperator.executeAndAwait(
    crossinline action: suspend (ReactiveTransaction) -> T?
): T? = execute { trx -> mono { action(trx) } }.next().awaitSingleOrNull()

// Exceção customizada (mantida para compatibilidade)
class PaymentProcessingException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)