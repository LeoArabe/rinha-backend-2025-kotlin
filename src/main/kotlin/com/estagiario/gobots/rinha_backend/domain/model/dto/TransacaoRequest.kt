package com.estagiario.gobots.rinha_backend.domain.model.dto

data class TransacaoRequest (
    val valor: Int,
    val tipo: String,
    val descricao: String
)