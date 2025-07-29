package com.estagiario.gobots.rinha_backend.application.worker

// >>>>> IMPORTS CORRIGIDOS <<<<<
import com.estagiario.gobots.rinha_backend.domain.PaymentEventStatus
import com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository.PaymentEventRepository
import com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository.PaymentRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
// >>>>> FIM DOS IMPORTS CORRIGIDOS <<<<<

@Component
class OutboxRelay(
    private val paymentEventRepository: PaymentEventRepository,
    private val paymentRepository: PaymentRepository,
    private val paymentProcessorWorker: PaymentProcessorWorker,
    private val redisTemplate: ReactiveStringRedisTemplate,
    @Value("\${app.instance-id:API-1}") private val instanceId: String
) {

    private val logger = KotlinLogging.logger {}
    private val isProcessing = AtomicBoolean(false)
    private val leaderKey = "rinha:leader:outbox-processor"

    // Coroutine scope para operações assíncronas
    private val processingScope = CoroutineScope(
        Dispatchers.Default + SupervisorJob() + CoroutineName("OutboxRelay")
    )

    /**
     * CORREÇÃO PRINCIPAL: Scheduler agora é uma função regular que delega
     * o trabalho assíncrono para uma coroutine.
     */
    @Scheduled(fixedDelay = 200) // Verifica por novos eventos a cada 200ms
    fun processOutboxEvents() {
        // Evita processamento concorrente
        if (!isProcessing.compareAndSet(false, true)) {
            logger.debug { "Processamento já em andamento, pulando..." }
            return
        }

        processingScope.launch {
            try {
                // Leader election para evitar processamento duplicado entre instâncias
                if (tryToBecomeLeader()) {
                    logger.debug { "Instância $instanceId é o líder do Outbox. Processando eventos..." }
                    processEventsAsync()
                } else {
                    logger.trace { "Instância $instanceId não é o líder do Outbox. Pulando processamento." }
                }
            } catch (e: Exception) {
                logger.error(e) { "Erro não tratado no processamento do Outbox" }
            } finally {
                isProcessing.set(false)
            }
        }
    }

    /**
     * NOVO: Lógica principal de processamento movida para função suspensa
     */
    private suspend fun processEventsAsync() {
        try {
            // Busca eventos pendentes
            val pendingEvents = paymentEventRepository
                .findAllByStatus(PaymentEventStatus.PENDING)
                .toList()

            if (pendingEvents.isEmpty()) {
                logger.trace { "Nenhum evento pendente encontrado" }
                return
            }

            logger.info { "Encontrados ${pendingEvents.size} eventos no Outbox para processar." }

            // Busca os pagamentos relacionados em uma única query
            val correlationIds = pendingEvents.map { it.correlationId }
            val paymentsMap = paymentRepository
                .findAllByCorrelationIdIn(correlationIds)
                .toList()
                .associateBy { it.correlationId }

            // Processa eventos em paralelo com controle de concorrência
            val processedCount = processEventsInParallel(pendingEvents, paymentsMap)

            logger.info { "Processamento do Outbox concluído: $processedCount/${pendingEvents.size} eventos processados" }

        } catch (e: Exception) {
            logger.error(e) { "Falha no processamento dos eventos do Outbox" }
            // Não relança a exceção para não quebrar o scheduler
        }
    }

    /**
     * OTIMIZAÇÃO: Processa eventos em paralelo com limite de concorrência
     */
    private suspend fun processEventsInParallel(
        pendingEvents: List<com.estagiario.gobots.rinha_backend.domain.PaymentEvent>,
        paymentsMap: Map<String, com.estagiario.gobots.rinha_backend.domain.Payment>
    ): Int = coroutineScope {
        val semaphore = Semaphore(permits = 16) // Máximo 16 processamentos paralelos

        val jobs = pendingEvents.map { event ->
            async {
                semaphore.withPermit {
                    val payment = paymentsMap[event.correlationId]
                    if (payment != null) {
                        try {
                            paymentProcessorWorker.processPaymentFromQueue(event, payment)
                            logger.debug { "Evento ${event.id} processado com sucesso" }
                            1 // >>>>> CORREÇÃO: RETORNO EXPLÍCITO <<<<<
                        } catch (e: Exception) {
                            logger.warn(e) { "Falha ao processar evento ${event.id} (correlationId=${event.correlationId})" }
                            0 // >>>>> CORREÇÃO: RETORNO EXPLÍCITO <<<<<
                        }
                    } else {
                        logger.warn { "Payment para o evento ${event.id} (correlationId=${event.correlationId}) não encontrado. Possível evento órfão." }
                        // TODO: Implementar dead letter queue para eventos órfãos
                        markEventAsOrphan(event)
                        0 // >>>>> CORREÇÃO: RETORNO EXPLÍCITO <<<<<
                    }
                }
            }
        }

        // Aguarda todos os jobs e soma os sucessos
        jobs.awaitAll().sum()
    }

    /**
     * NOVO: Leader election usando Redis para coordenação entre instâncias
     */
    private suspend fun tryToBecomeLeader(): Boolean {
        return try {
            val acquired = redisTemplate.opsForValue()
                .setIfAbsent(leaderKey, instanceId, Duration.ofSeconds(10)) // Aumentado TTL para 10s para ser consistente com o app.leader-election.ttl-seconds
                .awaitSingleOrNull() ?: false

            if (acquired) {
                logger.trace { "Lock de liderança adquirido para Outbox por $instanceId" }
            }

            acquired
        } catch (e: Exception) {
            logger.warn(e) { "Falha ao tentar adquirir liderança do Outbox. Processando localmente." }
            true // Em caso de falha no Redis, processa localmente para garantir funcionamento
        }
    }

    /**
     * NOVO: Marca eventos órfãos para investigação posterior
     */
    private suspend fun markEventAsOrphan(event: com.estagiario.gobots.rinha_backend.domain.PaymentEvent) {
        try {
            // Em um sistema real, moveríamos para uma "dead letter queue"
            // Por ora, apenas logamos e marcamos como processado para evitar loop infinito
            val orphanEvent = event.copy(
                status = PaymentEventStatus.PROCESSED,
                processedAt = Instant.now()
            )
            paymentEventRepository.save(orphanEvent)
            logger.warn { "Evento órfão ${event.id} marcado como processado para evitar reprocessamento" }
        } catch (e: Exception) {
            logger.error(e) { "Falha ao marcar evento órfão ${event.id}" }
        }
    }

    /**
     * NOVO: Limpeza de recursos quando o componente é destruído
     */
    @jakarta.annotation.PreDestroy
    fun cleanup() {
        logger.info { "Encerrando OutboxRelay..." }
        processingScope.cancel("Aplicação sendo encerrada")

        // Libera o lock de liderança se estivermos com ele
        try {
            redisTemplate.delete(leaderKey).subscribe()
        } catch (e: Exception) {
            logger.debug(e) { "Falha ao liberar lock de liderança na destruição" }
        }
    }

    /**
     * NOVO: Métricas e monitoramento
     */
    fun getProcessingStatus(): Map<String, Any> {
        return mapOf(
            "isProcessing" to isProcessing.get(),
            "instanceId" to instanceId,
            "scopeActive" to processingScope.isActive
        )
    }
}