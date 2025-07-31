package com.estagiario.gobots.rinha_backend.application.service.impl

import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.reactor.mono
import org.springframework.transaction.ReactiveTransaction
import org.springframework.transaction.reactive.TransactionalOperator
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono


/**
 * Extension function corrigida para uso de transações reativas com corrotinas.
 * Garante que o resultado da transação seja aguardado.
 */
suspend inline fun <T> TransactionalOperator.executeAndAwait(
    crossinline action: suspend (ReactiveTransaction) -> T
): T = execute { trx ->
    mono { action(trx) }
}.single().awaitSingle() // CORREÇÃO: Adicionado .single() para converter Flux em Mono