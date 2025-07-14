package com.estagiario.gobots.rinha_backend.domain.repository

import com.estagiario.gobots.rinha_backend.domain.model.Cliente
import org.springframework.data.mongodb.repository.ReactiveMongoRepository

interface ClienteRepository : ReactiveMongoRepository<Cliente, String>