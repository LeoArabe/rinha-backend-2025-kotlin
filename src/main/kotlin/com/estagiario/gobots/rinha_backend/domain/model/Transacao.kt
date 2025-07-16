package com.estagiario.gobots.rinha_backend.domain.model

data class Transacao(
    val valor: Int,
    val tipo: String,
    val descricao: String,
    val realizadaEm: String
)