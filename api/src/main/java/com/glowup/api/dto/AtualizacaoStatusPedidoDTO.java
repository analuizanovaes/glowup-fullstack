package com.glowup.api.dto;

import com.glowup.api.domain.StatusPedido;
import jakarta.validation.constraints.NotNull;

public record AtualizacaoStatusPedidoDTO(
    @NotNull(message = "O novo status do pedido é obrigatório")
    StatusPedido status
) {}
