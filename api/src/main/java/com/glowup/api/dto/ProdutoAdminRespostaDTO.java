package com.glowup.api.dto;

import com.glowup.api.repository.ProdutoAdminProjecao;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Linha da listagem da administradora: inclui inativos, kits e o custo de fábrica. */
public record ProdutoAdminRespostaDTO(
        Long id,
        String nome,
        String descricao,
        String marca,
        String categoria,
        String tipoKit,
        String modalidade,
        BigDecimal custoFabrica,
        BigDecimal precoBase,
        BigDecimal precoPromocional,
        LocalDateTime dataLimitePromocao,
        String imagemUrl,
        boolean ativo,
        Integer quantidade,
        Integer quantidadeReservada,
        LocalDate dataValidade,
        Integer tempoMedioUsoDias) {

    public static ProdutoAdminRespostaDTO de(ProdutoAdminProjecao p) {
        return new ProdutoAdminRespostaDTO(
                p.getId(),
                p.getNome(),
                p.getDescricao(),
                p.getMarca(),
                p.getCategoria(),
                p.getTipoKit(),
                p.getModalidade(),
                p.getCustoFabrica(),
                p.getPrecoBase(),
                p.getPrecoPromocional(),
                p.getDataLimitePromocao(),
                p.getImagemUrl(),
                Boolean.TRUE.equals(p.getAtivo()),
                p.getQuantidade(),
                p.getQuantidadeReservada(),
                p.getDataValidade(),
                p.getTempoMedioUsoDias()
        );
    }
}
