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

@Service
class PaymentServiceImpl(
    private val paymentRepository: PaymentRepository,
    private val paymentEventRepository: PaymentEventRepository,
    private val transactionalOperator: TransactionalOperator
) : PaymentService {

    override suspend fun processNewPayment(request: PaymentRequest) {
        // A extension function agora irá compilar corretamente
        transactionalOperator.executeAndAwait {
            val payment = request.toDomainEntity()
            paymentRepository.save(payment)

            val paymentEvent = PaymentEvent.newProcessPaymentEvent(payment.correlationId)
            paymentEventRepository.save(paymentEvent)
        }
    }
}

/**
 * Extension function para facilitar o uso de transações reativas com corrotinas.
 * O .next() converte o Flux resultante em um Mono, permitindo que o awaitSingleOrNull funcione.
 */
private suspend inline fun <T> TransactionalOperator.executeAndAwait(
    crossinline action: suspend (ReactiveTransaction) -> T?
): T? = execute { trx -> mono { action(trx) } }.next().awaitSingleOrNull()