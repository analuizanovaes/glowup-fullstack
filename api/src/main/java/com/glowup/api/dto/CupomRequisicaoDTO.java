package com.glowup.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Informe percentualDesconto OU valorDesconto (exclusivos, como exige a constraint do banco). */
public record CupomRequisicaoDTO(
    @NotBlank(message = "O código do cupom é obrigatório")
    @Size(max = 50, message = "O código deve ter no máximo 50 caracteres")
    String codigo,

    @DecimalMin(value = "0.01", message = "O percentual deve ser maior que zero")
    BigDecimal percentualDesconto,

    @DecimalMin(value = "0.01", message = "O valor de desconto deve ser maior que zero")
    BigDecimal valorDesconto,

    @DecimalMin(value = "0.00", message = "O valor mínimo não pode ser negativo")
    BigDecimal valorMinimo,

    @NotNull(message = "A data de início é obrigatória")
    LocalDateTime validadeInicio,

    @NotNull(message = "A data de fim é obrigatória")
    LocalDateTime validadeFim,

    @Min(value = 1, message = "O limite de usos deve ser de pelo menos 1")
    Integer limiteUso,

    Boolean ativo
) {}
