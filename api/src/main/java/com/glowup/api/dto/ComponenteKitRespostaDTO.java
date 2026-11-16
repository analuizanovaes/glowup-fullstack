package com.glowup.api.dto;

import com.glowup.api.domain.ItemKit;

public record ComponenteKitRespostaDTO(Long produtoId, String nome, String imagemUrl, Integer quantidade) {

    public static ComponenteKitRespostaDTO de(ItemKit item) {
        return new ComponenteKitRespostaDTO(
                item.getProduto() == null ? null : item.getProduto().getId(),
                item.getProduto() == null ? "Produto removido" : item.getProduto().getNome(),
                item.getProduto() == null ? null : item.getProduto().getImagemUrl(),
                item.getQuantidade()
        );
    }
}
