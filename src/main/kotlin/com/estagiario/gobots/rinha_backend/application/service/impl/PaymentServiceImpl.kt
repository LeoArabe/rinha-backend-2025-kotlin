// Caminho: src/main/kotlin/com/estagiario/gobots/rinha_backend/application/service/impl/PaymentServiceImpl.kt
package com.estagiario.gobots.rinha_backend.application.service.impl

import com.estagiario.gobots.rinha_backend.application.service.PaymentService
import com.estagiario.gobots.rinha_backend.domain.PaymentEvent
import com.estagiario.gobots.rinha_backend.domain.exception.PaymentProcessingException
import com.estagiario.gobots.rinha_backend.infrastructure.incoming.dto.PaymentRequest
import com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository.PaymentEventRepository
import com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository.PaymentRepository
import mu.KotlinLogging
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.reactive.TransactionalOperator

@Service
class PaymentServiceImpl(
    private val paymentRepository: PaymentRepository,
    private val paymentEventRepository: PaymentEventRepository,
    private val transactionalOperator: TransactionalOperator
) : PaymentService {

    private val logger = KotlinLogging.logger {}

    /**
     * VERSÃO FINAL E CORRETA: Usa a extensão TransactionalOperator.executeAndAwait
     * para garantir transações ACID.
     */
    override suspend fun processNewPayment(request: PaymentRequest) {
        logger.info { "🚀 Iniciando processamento do pagamento: ${request.correlationId}" }

        try {
            // Usa a função de extensão para transação ACID
            transactionalOperator.executeAndAwait { _ -> // 'trx' renomeado para '_'
                logger.debug { "📝 Criando entidade de pagamento dentro da transação..." }
                val payment = request.toDomainEntity()
                logger.info { "✅ Payment criado: ID=${payment.correlationId}, Status=${payment.status}, Amount=${payment.amount}" }

                logger.debug { "💾 Salvando payment no repositório (dentro da transação)..." }
                val savedPayment = paymentRepository.save(payment)
                logger.info { "✅ Payment salvo: ID=${savedPayment.correlationId}, Status=${savedPayment.status}" }

                logger.debug { "📤 Criando evento de pagamento (dentro da transação)..." }
                val paymentEvent = PaymentEvent.newProcessPaymentEvent(payment.correlationId)
                // CORREÇÃO AQUI: Usando correlationId, não paymentId
                logger.info { "✅ PaymentEvent criado: ID=${paymentEvent.id}, CorrelationID=${paymentEvent.correlationId}" }

                logger.debug { "💾 Salvando evento no repositório (dentro da transação)..." }
                val savedEvent = paymentEventRepository.save(paymentEvent)
                logger.info { "✅ PaymentEvent salvo: ID=${savedEvent.id}, Status=${savedEvent.status}" }
            }
            logger.info { "🎉 Transação de pagamento ${request.correlationId} commitada com sucesso!" }

        } catch (e: DuplicateKeyException) {
            logger.info { "🔄 Pagamento duplicado detectado: ${request.correlationId}" }
            throw e
        } catch (e: Exception) {
            logger.error(e) { "❌ Erro crítico ao processar pagamento ${request.correlationId}: ${e.message}" }
            logger.error { "Stack trace completo:" }
            e.printStackTrace()
            throw PaymentProcessingException("Falha ao persistir intenção de pagamento ${request.correlationId}", e)
        }
    }
}