package com.estagiario.gobots.rinha_backend.infrastructure.incoming.dto

import com.estagiario.gobots.rinha_backend.domain.Payment
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.math.BigDecimal
import java.text.DecimalFormat
import java.util.*

data class PaymentRequest(
    @field:NotBlank(message = "correlationId é obrigatório")
    val correlationId: String,

    @field:NotNull(message = "amount é obrigatório")
    @field:Positive(message = "amount deve ser positivo")
    val amount: BigDecimal
) {
    /**
     * Valida se o correlationId é um UUID válido
     */
    fun hasValidUuidFormat(): Boolean {
        return try {
            UUID.fromString(correlationId)
            true
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    /**
     * Formata o valor para exibição em logs (formato brasileiro)
     */
    fun getFormattedAmount(): String {
        val formatter = DecimalFormat.getCurrencyInstance(Locale.of("pt", "BR"))
        return formatter.format(amount)
    }

    /**
     * Converte o DTO para a entidade de domínio Payment
     * Converte o valor de BigDecimal (com decimais) para centavos (Long)
     */
    fun toDomainEntity(): Payment {
        // Multiplica por 100 para converter reais em centavos
        val amountInCents = amount.multiply(BigDecimal.valueOf(100)).toLong()
        return Payment.newPayment(
            correlationId = correlationId,
            amount = amountInCents
        )
    }
}