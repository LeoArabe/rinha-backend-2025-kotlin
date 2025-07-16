package com.estagiario.gobots.rinha_backend.application

import com.estagiario.gobots.rinha_backend.domain.model.Cliente
import com.estagiario.gobots.rinha_backend.domain.model.Transacao
import com.estagiario.gobots.rinha_backend.domain.repository.ClienteRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import java.time.Instant

@Service
class ClienteService(
    private val repository: ClienteRepository
) {

    fun criarCliente(cliente: Cliente): Mono<Cliente> {
        return repository.save(cliente)
    }

    fun buscarExtrato(id: String): Mono<Cliente> {
        return repository.findById(id)
    }

    fun registrarTransacao(id: String, transacao: Transacao): Mono<Cliente> {
        return repository.findById(id)
            .switchIfEmpty(Mono.error(ResponseStatusException(HttpStatus.NOT_FOUND)))
            .flatMap { cliente ->
                val novoSaldo = if (transacao.tipo == "c")
                    cliente.saldo + transacao.valor
                else
                    cliente.saldo - transacao.valor

                if (transacao.tipo == "d" && novoSaldo < -cliente.limite) {
                    return@flatMap Mono.error(ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY))
                }

                val novaTransacao = transacao.copy(realizadaEm = Instant.now().toString())

                val atualizado = cliente.copy(
                    saldo = novoSaldo,
                    transacoes = listOf(novaTransacao) + cliente.transacoes.take(9)
                )

                repository.save(atualizado)
            }
    }
}