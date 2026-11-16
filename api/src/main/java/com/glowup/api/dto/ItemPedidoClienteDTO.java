package com.glowup.api.dto;

import com.glowup.api.domain.ItemPedido;

import java.math.BigDecimal;

public record ItemPedidoClienteDTO(
    Long idItemPedido,
    Long produtoId,
    String produtoNome,
    Integer quantidade,
    BigDecimal precoUnitario
) {

    public static ItemPedidoClienteDTO de(ItemPedido item) {
        return new ItemPedidoClienteDTO(
                item.getId(),
                item.getProduto() != null ? item.getProduto().getId() : null,
                item.getProduto() != null ? item.getProduto().getNome() : null,
                item.getQuantidade(),
                item.getPrecoUnitario());
    }
}
