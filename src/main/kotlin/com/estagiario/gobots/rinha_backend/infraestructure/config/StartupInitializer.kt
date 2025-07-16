package com.estagiario.gobots.rinha_backend.infraestructure.config

import com.estagiario.gobots.rinha_backend.domain.model.Cliente
import com.estagiario.gobots.rinha_backend.domain.repository.ClienteRepository
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import reactor.core.publisher.Flux

@Configuration
class StartupInitializer {
    @Bean
    fun preloadClientes(repository: ClienteRepository): ApplicationRunner = ApplicationRunner {
        repository.count()
            .filter { it == 0L }
            .flatMapMany {
                Flux.just(
                    Cliente("1", "Cliente 1", limite = 100_000, saldo = 0),
                    Cliente("2", "Cliente 2", limite = 80_000, saldo = 0),
                    Cliente("3", "Cliente 3", limite = 1_000_000, saldo = 0),
                    Cliente("4", "Cliente 4", limite = 10_000_000, saldo = 0),
                    Cliente("5", "Cliente 5", limite = 500_000, saldo = 0),
                )
            }
            .flatMap(repository::save)
            .doOnNext { println("[Startup] Cliente pré-carregado: ${it.id} - ${it.nome}") }
            .subscribe()
    }
}