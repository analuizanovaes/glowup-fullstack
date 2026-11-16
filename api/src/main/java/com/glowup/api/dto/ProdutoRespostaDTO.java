package com.glowup.api.dto;

import com.glowup.api.domain.ModalidadeProduto;
import com.glowup.api.domain.Produto;
import com.glowup.api.service.EstoqueDisponibilidadeService;
import java.math.BigDecimal;

/**
 * Vitrine do produto. Não expõe custo de fábrica nem dados internos de estoque.
 * quantidadeDisponivel é nulo quando não há limite (Sob Encomenda).
 */
public record ProdutoRespostaDTO(
        Long id,
        String nome,
        String descricao,
        String marca,
        String categoria,
        BigDecimal precoBase,
        BigDecimal precoPromocional,
        BigDecimal precoVigente,
        String modalidade,
        String imagemUrl,
        Integer quantidadeDisponivel,
        Integer prazoEntregaDias) {

    private static final EstoqueDisponibilidadeService DISPONIBILIDADE = new EstoqueDisponibilidadeService();

    public static ProdutoRespostaDTO de(Produto p) {
        BigDecimal vigente = p.obterPrecoVigente();
        boolean emPromocao = p.getPrecoPromocional() != null && vigente.compareTo(p.getPrecoPromocional()) == 0;
        return new ProdutoRespostaDTO(
                p.getId(),
                p.getNome(),
                p.getDescricao(),
                p.getMarca(),
                p.getCategoria().getNome(),
                p.getPrecoBase(),
                emPromocao ? p.getPrecoPromocional() : null,
                vigente,
                p.getModalidade().name(),
                p.getImagemUrl(),
                quantidadeDisponivel(p),
                p.getPrazoEntregaDias()
        );
    }

    static Integer quantidadeDisponivel(Produto p) {
        if (p.getModalidade() == ModalidadeProduto.SOB_ENCOMENDA) {
            return null;
        }
        int disponivel = DISPONIBILIDADE.calcular(p);
        return disponivel == EstoqueDisponibilidadeService.ILIMITADO ? null : disponivel;
    }
}