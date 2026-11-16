package com.glowup.api.dto;

import com.glowup.api.domain.ItemDevolucao;

public record ItemDevolucaoRespostaDTO(
        Long id,
        Long idItemPedido,
        Long produtoId,
        String produtoNome,
        Integer quantidade,
        Boolean produtoLacrado) {

    public static ItemDevolucaoRespostaDTO de(ItemDevolucao item) {
        return new ItemDevolucaoRespostaDTO(item.getId(),
                item.getItemPedido() == null ? null : item.getItemPedido().getId(),
                item.getProduto() == null ? null : item.getProduto().getId(),
                item.getProduto() == null ? "Produto removido" : item.getProduto().getNome(),
                item.getQuantidade(), item.getProdutoLacrado());
    }
}
