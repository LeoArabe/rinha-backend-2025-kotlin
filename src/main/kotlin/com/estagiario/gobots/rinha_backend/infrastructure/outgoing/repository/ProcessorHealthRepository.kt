package com.estagiario.gobots.rinha_backend.infrastructure.outgoing.repository

import com.estagiario.gobots.rinha_backend.domain.ProcessorHealth
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import org.springframework.stereotype.Repository

@Repository
interface ProcessorHealthRepository : ReactiveMongoRepository<ProcessorHealth, String>
