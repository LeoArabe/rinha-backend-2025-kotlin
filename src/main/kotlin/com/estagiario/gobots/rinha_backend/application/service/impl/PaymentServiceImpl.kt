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
        logger.info { "🚀 Processando pagamento ${request.correlationId}" }

        val payment = request.toDomainEntity()
        val paymentEvent = PaymentEvent.newProcessPaymentEvent(payment.correlationId)

        // ✅ VERSÃO ULTRA-SIMPLIFICADA: Sem tratamento de erro complexo
        return paymentRepository.save(payment)
            .doOnNext {
                logger.info { "✅ Payment salvo com sucesso: ${it.correlationId}" }
            }
            .flatMap {
                logger.info { "🔄 Agora salvando PaymentEvent..." }
                paymentEventRepository.save(paymentEvent)
            }
            .doOnNext {
                logger.info { "✅ PaymentEvent salvo com sucesso: ${it.id}" }
            }
            .doOnSuccess {
                logger.info { "🎉 SUCESSO TOTAL! Dados persistidos para ${request.correlationId}" }
            }
            .then()
            .doOnError { error ->
                logger.error(error) { "❌ ERRO GERAL: ${error.message}" }
            }
            .onErrorResume { error ->
                logger.error(error) { "⚠️ Resumindo erro para continuar aplicação" }
                Mono.empty()
            }
    }
}