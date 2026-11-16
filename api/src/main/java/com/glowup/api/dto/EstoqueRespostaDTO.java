package com.glowup.api.dto;

import com.glowup.api.domain.Estoque;

import java.time.LocalDate;

public record EstoqueRespostaDTO(
    Long produtoId,
    String produto,
    int quantidade,
    int quantidadeReservada,
    int disponivel,
    int quantidadeMinima,
    boolean abaixoDoMinimo,
    LocalDate dataValidade
) {
    public static EstoqueRespostaDTO de(Estoque estoque) {
        int disponivel = estoque.getQuantidade() - estoque.getQuantidadeReservada();
        return new EstoqueRespostaDTO(
                estoque.getProduto().getId(),
                estoque.getProduto().getNome(),
                estoque.getQuantidade(),
                estoque.getQuantidadeReservada(),
                disponivel,
                estoque.getQuantidadeMinima(),
                disponivel <= estoque.getQuantidadeMinima(),
                estoque.getDataValidade());
    }
}
