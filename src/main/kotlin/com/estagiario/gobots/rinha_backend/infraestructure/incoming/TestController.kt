package com.estagiario.gobots.rinha_backend.infraestructure.incoming

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class RootController {

    @GetMapping("/")
    fun home(): String {
        return "API está funcionando!"
    }
}
