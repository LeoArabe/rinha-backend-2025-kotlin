package com.estagiario.gobots.rinha_backend.infrastructure.incoming.dto

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal

/**
 * DTO for incoming payment requests.
 */
data class PaymentRequest(
    @field:NotNull(message = "Amount cannot be null")
    @field:DecimalMin(value = "0.01", message = "Amount must be positive")
    @field:Digits(integer = 10, fraction = 2, message = "Amount must have up to 2 decimal places")
    val amount: BigDecimal,

    // This field will be populated by the controller, not the client.
    @JsonIgnore
    val correlationId: String = ""
)