package com.glowup.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** Define o novo saldo físico (contagem de inventário). */
public record EstoqueAjusteDTO(
    @NotNull(message = "O novo saldo é obrigatório")
    @Min(value = 0, message = "O saldo não pode ser negativo")
    Integer quantidade,

    @Min(value = 0, message = "O estoque mínimo não pode ser negativo")
    Integer quantidadeMinima,

    LocalDate dataValidade,

    String observacao
) {}
