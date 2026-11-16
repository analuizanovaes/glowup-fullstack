package com.glowup.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ItemKitRequisicaoDTO(
    @NotNull(message = "O ID do produto componente é obrigatório")
    Long produtoId,

    @NotNull(message = "A quantidade do componente é obrigatória")
    @Positive(message = "A quantidade do componente deve ser maior que zero")
    Integer quantidade
) {}
