package com.estagiario.gobots.rinha_backend

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling // <-- ADICIONE ESTE IMPORT

@SpringBootApplication
@EnableScheduling
class RinhaBackendApplication

fun main(args: Array<String>) {
	runApplication<RinhaBackendApplication>(*args)
}