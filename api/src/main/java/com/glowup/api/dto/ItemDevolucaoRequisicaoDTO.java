package com.glowup.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ItemDevolucaoRequisicaoDTO(
    @NotNull(message = "O ID do item do pedido é obrigatório")
    Long idItemPedido,

    @NotNull(message = "A quantidade é obrigatória")
    @Positive(message = "A quantidade deve ser maior que zero")
    Integer quantidade
) {}