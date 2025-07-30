package com.estagiario.gobots.rinha_backend.application.service.impl

import kotlinx.coroutines.reactor.awaitSingle // <-- ADICIONE ESTE IMPORT
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.reactor.mono
import org.springframework.transaction.ReactiveTransaction
import org.springframework.transaction.reactive.TransactionalOperator
import reactor.core.publisher.Flux // Importe Flux se ainda não estiver importado


/**
 * Extension function corrigida para uso de transações reativas com corrotinas.
 * Garante que o resultado da transação seja aguardado.
 */
suspend inline fun <T> TransactionalOperator.executeAndAwait(
    crossinline action: suspend (ReactiveTransaction) -> T
): T = execute { trx ->
    mono { action(trx) }
}.single().awaitSingle() // <-- CORREÇÃO AQUI: Adicionado .single() para converter Flux em Mono