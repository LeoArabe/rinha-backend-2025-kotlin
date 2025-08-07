// CAMINHO: src/main/kotlin/com/estagiario/gobots/rinha_backend/infrastructure/incoming/dto/PaymentRequest.kt

package com.estagiario.gobots.rinha_backend.infrastructure.incoming.dto

import com.estagiario.gobots.rinha_backend.domain.Payment
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal

data class PaymentRequest(
    @JsonProperty("correlationId")
    @field:NotBlank(message = "correlationId é obrigatório e não pode ser vazio")
    val correlationId: String,

    /**
     * ✅ CORRETO: O DTO recebe o 'amount' como BigDecimal,
     * exatamente como o cliente envia no JSON (ex: 25.50).
     * A validação @DecimalMin funciona corretamente aqui.
     */
    @JsonProperty("amount")
    @field:NotNull(message = "amount é obrigatório")
    @field:DecimalMin(value = "0.01", message = "amount deve ser maior que zero")
    val amount: BigDecimal
) {

    /**
     * ✅ CORRETO: A conversão para centavos (Long) acontece aqui, na chamada
     * para criar a entidade de domínio.
     * É aqui que 25.50 (BigDecimal) se torna 2550 (Long).
     */
    fun toDomainEntity(): Payment {
        return Payment.newPayment(
            correlationId = this.correlationId,
            amount = this.amount.multiply(BigDecimal(100)).toLong()
        )
    }

    fun hasValidUuidFormat(): Boolean {
        return try {
            java.util.UUID.fromString(correlationId)
            true
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    // Esta função auxiliar continua útil para logs
    fun getFormattedAmount(): String {
        return amount.stripTrailingZeros().toPlainString()
    }
}