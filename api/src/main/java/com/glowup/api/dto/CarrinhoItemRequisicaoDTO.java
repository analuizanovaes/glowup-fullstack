package com.glowup.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CarrinhoItemRequisicaoDTO(
    @NotNull(message = "O produto é obrigatório")
    Long produtoId,

    @NotNull(message = "A quantidade é obrigatória")
    @Min(value = 1, message = "A quantidade mínima é 1")
    @Max(value = 99, message = "A quantidade máxima por item é 99")
    Integer quantidade
) {}
