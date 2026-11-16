package com.glowup.api.dto;

import com.glowup.api.domain.ItemPedido;

import java.math.BigDecimal;

public record ItemPedidoAdminDTO(
    Long produtoId,
    String produtoNome,
    Integer quantidade,
    BigDecimal precoUnitario,
    BigDecimal custoFabrica
) {

    public static ItemPedidoAdminDTO de(ItemPedido item) {
        return new ItemPedidoAdminDTO(
                item.getProduto() != null ? item.getProduto().getId() : null,
                item.getProduto() != null ? item.getProduto().getNome() : "Produto removido",
                item.getQuantidade(),
                item.getPrecoUnitario(),
                item.getCustoFabrica());
    }
}
