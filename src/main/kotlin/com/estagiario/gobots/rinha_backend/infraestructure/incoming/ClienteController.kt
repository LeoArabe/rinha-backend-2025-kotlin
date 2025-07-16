package com.estagiario.gobots.rinha_backend.infraestructure.incoming

import com.estagiario.gobots.rinha_backend.application.ClienteService
import com.estagiario.gobots.rinha_backend.domain.model.Cliente
import com.estagiario.gobots.rinha_backend.domain.model.Transacao
import com.estagiario.gobots.rinha_backend.domain.model.dto.TransacaoRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/clientes")
class ClienteController(
    private val service: ClienteService
) {

    @PostMapping
    fun criarCliente(@RequestBody cliente: Cliente): Mono<Cliente> =
        service.criarCliente(cliente)

    @GetMapping("/{id}/extrato")
    fun extrato(@PathVariable id: String): Mono<Cliente> =
        service.buscarExtrato(id)

    @PostMapping("/{id}/transacoes")
    fun transacao(
        @PathVariable id: String,
        @RequestBody transacaoReq: TransacaoRequest
    ): Mono<Cliente> {
        val transacao = Transacao(
            valor = transacaoReq.valor,
            tipo = transacaoReq.tipo,
            descricao = transacaoReq.descricao,
            realizadaEm = ""
        )
        return service.registrarTransacao(id, transacao)
    }
}
