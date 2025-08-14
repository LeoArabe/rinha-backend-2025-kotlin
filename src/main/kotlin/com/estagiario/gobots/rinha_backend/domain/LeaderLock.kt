package com.estagiario.gobots.rinha_backend.domain

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document("leader_locks")
data class LeaderLock(
    @Id
    val key: String, // "health-check-leader"
    val owner: String, // instance-id
    val expireAt: Instant
) {
    fun isExpired(): Boolean = Instant.now().isAfter(expireAt)

    fun isOwnedBy(instanceId: String): Boolean = owner == instanceId
}