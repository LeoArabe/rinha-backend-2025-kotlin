package com.estagiario.gobots.rinha_backend.domain.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document("clientes")
data class Cliente(
    @Id val id: String? = null,
    val nome: String,
    val saldo: Int = 0,
    val transacoes: List<Transacao> = emptyList()
)