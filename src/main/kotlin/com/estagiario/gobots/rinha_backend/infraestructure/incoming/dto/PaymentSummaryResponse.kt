package com.estagiario.gobots.rinha_backend.infrastructure.incoming.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

/**
 * DTO de resposta para o endpoint GET /payments-summary
 *
 * CORRIGIDO: Estrutura agora segue exatamente as regras da Rinha
 * A resposta deve ter objetos separados para "default" e "fallback"
 * conforme especificado na documentação oficial.
 */
data class PaymentSummaryResponse(

    /**
     * Resumo dos pagamentos processados pelo processador padrão (mais barato)
     */
    @JsonProperty("default")
    val defaultProcessor: ProcessorSummary,

    /**
     * Resumo dos pagamentos processados pelo processador de fallback (mais caro)
     */
    @JsonProperty("fallback")
    val fallbackProcessor: ProcessorSummary
) {

    companion object {
        /**
         * Cria uma resposta vazia quando não há pagamentos processados
         */
        fun empty(): PaymentSummaryResponse {
            return PaymentSummaryResponse(
                defaultProcessor = ProcessorSummary.empty(),
                fallbackProcessor = ProcessorSummary.empty()
            )
        }

        /**
         * Cria a resposta a partir dos dados agregados por processador
         */
        fun from(
            defaultTotalRequests: Long,
            defaultTotalAmount: BigDecimal,
            fallbackTotalRequests: Long,
            fallbackTotalAmount: BigDecimal
        ): PaymentSummaryResponse {
            return PaymentSummaryResponse(
                defaultProcessor = ProcessorSummary(
                    totalRequests = defaultTotalRequests,
                    totalAmount = defaultTotalAmount.stripTrailingZeros()
                ),
                fallbackProcessor = ProcessorSummary(
                    totalRequests = fallbackTotalRequests,
                    totalAmount = fallbackTotalAmount.stripTrailingZeros()
                )
            )
        }
    }

    /**
     * Calcula o total consolidado de todos os processadores
     */
    fun getTotalAmount(): BigDecimal {
        return defaultProcessor.totalAmount.add(fallbackProcessor.totalAmount)
    }

    /**
     * Calcula o total de requests de todos os processadores
     */
    fun getTotalRequests(): Long {
        return defaultProcessor.totalRequests + fallbackProcessor.totalRequests
    }
}

/**
 * Representa o resumo de um processador específico
 * Estrutura exigida pelas regras da Rinha
 */
data class ProcessorSummary(

    /**
     * Número total de pagamentos processados com SUCESSO neste processador
     */
    @JsonProperty("totalRequests")
    val totalRequests: Long,

    /**
     * Valor total de todos os pagamentos processados com SUCESSO neste processador
     */
    @JsonProperty("totalAmount")
    val totalAmount: BigDecimal
) {

    companion object {
        fun empty(): ProcessorSummary {
            return ProcessorSummary(
                totalRequests = 0L,
                totalAmount = BigDecimal.ZERO
            )
        }
    }

    /**
     * Valida se o resumo está consistente
     */
    fun isValid(): Boolean {
        return totalRequests >= 0L && totalAmount >= BigDecimal.ZERO &&
                (totalRequests == 0L && totalAmount == BigDecimal.ZERO || totalRequests > 0L)
    }
}