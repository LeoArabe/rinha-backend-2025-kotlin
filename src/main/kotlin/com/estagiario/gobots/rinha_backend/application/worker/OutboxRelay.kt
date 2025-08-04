// CAMINHO: src/main/kotlin/com/estagiario/gobots/rinha_backend/application/worker/OutboxRelay.kt

package com.estagiario.gobots.rinha_backend.application.worker

import com.estagiario.gobots.rinha_backend.domain.PaymentEventStatus
import com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository.PaymentEventRepository
import com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository.PaymentRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

@Component
class OutboxRelay(
    private val paymentEventRepository: PaymentEventRepository,
    private val paymentRepository: PaymentRepository,
    private val paymentProcessorWorker: PaymentProcessorWorker,
    private val redisTemplate: ReactiveStringRedisTemplate,
    @Value("\${app.instance-id:API-LOCAL}") private val instanceId: String,
    @Value("\${app.outbox.leader-ttl-seconds:5}") private val leaderTtl: Long
) {

    private val logger = KotlinLogging.logger {}
    private val isProcessing = AtomicBoolean(false)
    private val leaderKey = "rinha:leader:outbox-processor"
    private val processingScope = CoroutineScope(Dispatchers.Default + SupervisorJob() + CoroutineName("OutboxRelay"))

    @Scheduled(fixedDelayString = "\${app.outbox.processing-delay-ms:200}")
    fun scheduleOutboxProcessing() {
        if (!isProcessing.compareAndSet(false, true)) {
            return
        }
        processingScope.launch {
            try {
                if (tryToBecomeLeader()) {
                    processEvents()
                }
            } finally {
                isProcessing.set(false)
            }
        }
    }

    private suspend fun processEvents() {
        try {
            // ✅ CORREÇÃO: Converte o Flux<PaymentEvent> retornado pelo repositório para um Flow de corrotinas.
            val pendingEvents = paymentEventRepository
                .findAllByStatus(PaymentEventStatus.PENDING)
                .asFlow()
                .toList()

            if (pendingEvents.isEmpty()) return

            logger.info { "Encontrados ${pendingEvents.size} eventos no Outbox para processar." }

            val correlationIds = pendingEvents.map { it.correlationId }
            // ✅ CORREÇÃO: Converte o Flux<Payment> para Flow<Payment> e depois para um mapa.
            val paymentsMap = paymentRepository
                .findAllByCorrelationIdIn(correlationIds)
                .asFlow()
                .toList()
                .associateBy { it.correlationId }

            val processedCount = processInParallel(pendingEvents, paymentsMap)
            logger.info { "Processamento do Outbox concluído: $processedCount/${pendingEvents.size} eventos processados." }

        } catch (e: Exception) {
            logger.error(e) { "Falha crítica durante o processamento de eventos do Outbox" }
        }
    }

    private suspend fun processInParallel(
        events: List<com.estagiario.gobots.rinha_backend.domain.PaymentEvent>,
        payments: Map<String, com.estagiario.gobots.rinha_backend.domain.Payment>
    ): Int = coroutineScope {
        val semaphore = Semaphore(permits = 16)
        events.map { event ->
            async {
                semaphore.withPermit {
                    payments[event.correlationId]?.let { payment ->
                        try {
                            paymentProcessorWorker.processPaymentFromQueue(event, payment)
                            1
                        } catch (e: Exception) {
                            logger.warn(e) { "Falha ao processar o evento ${event.id} para o pagamento ${payment.correlationId}" }
                            0
                        }
                    } ?: run {
                        logger.warn { "Payment para o evento ${event.id} (correlationId=${event.correlationId}) não encontrado." }
                        0
                    }
                }
            }
        }.awaitAll().sum()
    }

    private suspend fun tryToBecomeLeader(): Boolean {
        return try {
            redisTemplate.opsForValue()
                .setIfAbsent(leaderKey, instanceId, Duration.ofSeconds(leaderTtl))
                .awaitSingleOrNull() ?: false
        } catch (e: Exception) {
            logger.warn(e) { "Falha ao comunicar com o Redis para eleição de líder. Assumindo liderança localmente." }
            true
        }
    }
}