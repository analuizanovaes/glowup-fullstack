package com.glowup.api.dto;

import com.glowup.api.domain.Kit;
import com.glowup.api.domain.TipoKit;
import java.math.BigDecimal;
import java.util.List;

/** Vitrine do kit. Os componentes só vêm preenchidos nos kits MANUAL. */
public record KitRespostaDTO(
        Long id,
        String nome,
        String descricao,
        String marca,
        String categoria,
        BigDecimal precoBase,
        BigDecimal precoPromocional,
        BigDecimal precoVigente,
        String tipo,
        String modalidade,
        String imagemUrl,
        Integer quantidadeDisponivel,
        Integer prazoEntregaDias,
        List<ComponenteKitRespostaDTO> componentes) {

    public static KitRespostaDTO de(Kit kit) {
        BigDecimal vigente = kit.obterPrecoVigente();
        boolean emPromocao = kit.getPrecoPromocional() != null && vigente.compareTo(kit.getPrecoPromocional()) == 0;
        List<ComponenteKitRespostaDTO> componentes = kit.getTipo() == TipoKit.MANUAL
                ? kit.getItens().stream().map(ComponenteKitRespostaDTO::de).toList()
                : List.of();
        return new KitRespostaDTO(
                kit.getId(),
                kit.getNome(),
                kit.getDescricao(),
                kit.getMarca(),
                kit.getCategoria().getNome(),
                kit.getPrecoBase(),
                emPromocao ? kit.getPrecoPromocional() : null,
                vigente,
                kit.getTipo().name(),
                kit.getModalidade().name(),
                kit.getImagemUrl(),
                ProdutoRespostaDTO.quantidadeDisponivel(kit),
                kit.getPrazoEntregaDias(),
                componentes
        );
    }
}