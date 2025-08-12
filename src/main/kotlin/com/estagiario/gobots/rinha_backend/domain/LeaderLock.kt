package com.estagiario.gobots.rinha_backend.domain

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document("leader_locks")
data class LeaderLock(
    @Id
    val key: String,
    var owner: String,
    var expireAt: Instant
)