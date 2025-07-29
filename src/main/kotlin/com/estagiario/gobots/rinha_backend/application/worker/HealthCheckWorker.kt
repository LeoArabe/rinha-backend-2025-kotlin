package com.estagiario.gobots.rinha_backend.application.worker

interface HealthCheckWorker {
    suspend fun monitorProcessorsHealth()
}