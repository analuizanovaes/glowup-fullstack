package com.glowup.api.dto;

import jakarta.validation.constraints.NotNull;

public record ItemLacreDTO(
        @NotNull Long idItemDevolucao,
        @NotNull Boolean produtoLacrado) {
}
