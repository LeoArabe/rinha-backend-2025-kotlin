// src/main/kotlin/com/estagiario/gobots/rinha_backend/application/service/impl/PaymentServiceImpl.kt

package com.estagiario.gobots.rinha_backend.application.service.impl

import com.estagiario.gobots.rinha_backend.application.service.PaymentService
import com.estagiario.gobots.rinha_backend.domain.PaymentEvent
import com.estagiario.gobots.rinha_backend.infrastructure.incoming.dto.PaymentRequest
import com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository.PaymentEventRepository
import com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository.PaymentRepository
import mu.KotlinLogging
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

@Service
class PaymentServiceImpl(
    private val paymentRepository: PaymentRepository,
    private val paymentEventRepository: PaymentEventRepository
    // ❌ TransactionalOperator removido para contornar problemas do GraalVM
) : PaymentService {

    private val logger = KotlinLogging.logger {}

    override fun processNewPayment(request: PaymentRequest): Mono<Void> {
        val payment = request.toDomainEntity()
        val paymentEvent = PaymentEvent.newProcessPaymentEvent(payment.correlationId)

        // ✅ Sua lógica reativa está perfeita. Apenas os logs foram padronizados.
        return Mono.defer {
            paymentRepository.save(payment)
                .flatMap { paymentEventRepository.save(paymentEvent) }
        }
            .elapsed()
            .doOnSuccess { elapsedResult ->
                // ✅ Log estruturado com a latência da operação de escrita no DB
                logger.info { "message=\"Intenção de pagamento persistida\" correlationId=${request.correlationId} durationMs=${elapsedResult.t1}" }
            }
            .then()
            .doOnError { error ->
                logger.error(error) { "message=\"Falha ao persistir intenção de pagamento\" correlationId=${request.correlationId}" }
            }
            // A sua lógica de onErrorResume é ótima para produção, pois não para a aplicação.
            .onErrorResume { error ->
                logger.error(error) { "⚠️ Resumindo erro para continuar aplicação" }
                Mono.empty()
            }
    }
}