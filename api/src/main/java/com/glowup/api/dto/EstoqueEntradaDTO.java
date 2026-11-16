package com.glowup.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record EstoqueEntradaDTO(
    @NotNull(message = "A quantidade é obrigatória")
    @Min(value = 1, message = "A entrada deve ser de pelo menos 1 unidade")
    Integer quantidade,

    LocalDate dataValidade,

    String observacao
) {}
