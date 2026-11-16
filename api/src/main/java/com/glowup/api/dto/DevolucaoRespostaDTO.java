package com.glowup.api.dto;

import com.glowup.api.domain.Devolucao;
import com.glowup.api.domain.StatusDevolucao;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DevolucaoRespostaDTO(
    Long id,
    Long pedidoId,
    StatusDevolucao status,
    String motivo,
    boolean arrependimento,
    Boolean produtoLacrado,
    String observacaoAnalise,
    LocalDateTime dataSolicitacao,
    LocalDateTime dataAnalise,
    LocalDateTime dataRecebimento,
    BigDecimal valorEstornado,
    java.util.List<ItemDevolucaoRespostaDTO> itens
) {

    public static DevolucaoRespostaDTO de(Devolucao devolucao, Long pedidoId) {
        return new DevolucaoRespostaDTO(
                devolucao.getId(),
                pedidoId,
                devolucao.getStatus(),
                devolucao.getMotivo(),
                devolucao.isArrependimento(),
                devolucao.getProdutoLacrado(),
                devolucao.getObservacaoAnalise(),
                devolucao.getDataSolicitacao(),
                devolucao.getDataAnalise(),
                devolucao.getDataRecebimento(),
                devolucao.getValorEstornado(),
                devolucao.getItens() == null ? java.util.List.of()
                        : devolucao.getItens().stream().map(ItemDevolucaoRespostaDTO::de).toList());
    }
}
