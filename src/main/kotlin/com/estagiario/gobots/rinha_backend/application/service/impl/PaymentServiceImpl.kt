// ATUALIZE ESTE FICHEIRO PARA A SUA VERSÃO FINAL:
// src/main/kotlin/com/estagiario/gobots/rinha_backend/application/service/impl/PaymentServiceImpl.kt

package com.estagiario.gobots.rinha_backend.application.service.impl

import com.estagiario.gobots.rinha_backend.application.service.PaymentService
import com.estagiario.gobots.rinha_backend.application.worker.TransactionalPersistenceWorker
import com.estagiario.gobots.rinha_backend.infrastructure.incoming.dto.PaymentRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactor.asCoroutineContext
import mu.KotlinLogging
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

@Service
class PaymentServiceImpl(
    private val persistenceWorker: TransactionalPersistenceWorker,
    private val applicationScope: CoroutineScope
) : PaymentService {

    private val logger = KotlinLogging.logger {}

    // A função principal agora retorna um Mono<Void> para se integrar com o WebFlux e capturar o contexto
    override fun processNewPayment(request: PaymentRequest): Mono<Void> {
        logger.info { "🚀 Intenção de pagamento ${request.correlationId} recebida. Lançando para persistência transacional." }

        // ✅ CAPTURA O CONTEXTO REATIVO ATUAL (QUE CONTÉM A TRANSAÇÃO)
        return Mono.deferContextual { contextView ->
            applicationScope.launch(contextView.asCoroutineContext()) { // ✅ PROPAGA O CONTEXTO PARA A NOVA CORROTINA
                try {
                    persistenceWorker.savePaymentAndEvent(request)
                } catch (e: DuplicateKeyException) {
                    logger.info { "🔄 Pagamento duplicado ${request.correlationId} detetado e ignorado." }
                } catch (e: Exception) {
                    logger.error(e) { "❌ Erro crítico ao persistir pagamento ${request.correlationId} em background." }
                }
            }
            Mono.empty<Void>()
        }
    }
}