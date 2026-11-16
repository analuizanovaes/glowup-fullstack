package com.glowup.api.dto;

import com.glowup.api.domain.AlertaValidade;
import com.glowup.api.domain.StatusAlertaValidade;

import java.time.LocalDate;

public record AlertaValidadeRespostaDTO(
    Long id,
    Long produtoId,
    String produto,
    LocalDate dataValidade,
    int diasRestantes,
    String mensagem,
    StatusAlertaValidade status
) {
    public static AlertaValidadeRespostaDTO de(AlertaValidade a) {
        return new AlertaValidadeRespostaDTO(
                a.getId(),
                a.getEstoque().getProduto().getId(),
                a.getEstoque().getProduto().getNome(),
                a.getDataPrevista(),
                a.getDiasRestantes(),
                a.getMensagem(),
                a.getStatus());
    }
}
