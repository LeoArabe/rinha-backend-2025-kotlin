package com.estagiario.gobots.rinha_backend.infrastructure.incoming.dto

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal

/**
 * DTO de requisição para o endpoint POST /payments
 *
 * Representa a intenção de pagamento enviada pelo cliente.
 * Este DTO é o ponto de entrada do sistema e deve ser extremamente
 * rápido de processar, garantindo apenas validações básicas antes
 * de persistir a intenção e retornar sucesso ao cliente.
 *
 * O processamento real acontece de forma assíncrona em background
 * através do padrão Outbox implementado com PaymentEvent.
 */
data class PaymentRequest(

    /**
     * ID único de correlação do pagamento fornecido pelo cliente
     *
     * REGRA CRÍTICA: Este ID é usado como chave primária na collection
     * de payments, garantindo idempotência natural. Se o mesmo correlationId
     * for enviado múltiplas vezes, o sistema deve lidar de forma consistente.
     *
     * Deve ser um UUID válido ou string não vazia fornecida pelo cliente.
     */
    @JsonProperty("correlationId")
    @field:NotBlank(message = "correlationId é obrigatório e não pode ser vazio")
    val correlationId: String,

    /**
     * Valor do pagamento em formato decimal
     *
     * IMPORTANTE: Recebemos como BigDecimal para evitar problemas de
     * precisão com ponto flutuante. O valor deve ser positivo e
     * maior que zero (não aceitamos pagamentos de valor zero).
     *
     * Exemplos válidos: 19.90, 100.00, 0.01
     * Exemplos inválidos: 0, -10.50, null
     */
    @JsonProperty("amount")
    @field:NotNull(message = "amount é obrigatório")
    @field:DecimalMin(value = "0.01", message = "amount deve ser maior que zero")
    val amount: BigDecimal
) {

    /**
     * Converte este DTO em uma nova entidade Payment do domínio
     *
     * Esta é a ponte entre a camada de infraestrutura (DTOs) e
     * a camada de domínio (entidades). Garante que a conversão
     * seja explícita e controlada.
     *
     * @return Nova instância de Payment no estado RECEBIDO
     */
    fun toDomainEntity(): com.estagiario.gobots.rinha_backend.domain.Payment {
        return com.estagiario.gobots.rinha_backend.domain.Payment.newPayment(
            correlationId = this.correlationId,
            amount = this.amount
        )
    }

    /**
     * Valida se o correlationId tem formato de UUID válido
     *
     * OPCIONAL: Validação adicional além das anotações Bean Validation.
     * Pode ser útil para garantir que o correlationId segue um padrão
     * específico esperado pelos sistemas clientes.
     *
     * @return true se o correlationId é um UUID válido
     */
    fun hasValidUuidFormat(): Boolean {
        return try {
            java.util.UUID.fromString(correlationId)
            true
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    /**
     * Formata o amount para exibição, removendo zeros desnecessários
     *
     * Útil para logs e debugging, garantindo que valores como
     * 19.90 não apareçam como 19.9000000000 nos logs.
     *
     * @return String formatada do valor (ex: "19.90")
     */
    fun getFormattedAmount(): String {
        return amount.stripTrailingZeros().toPlainString()
    }
}