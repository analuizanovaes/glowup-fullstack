package com.glowup.api.dto;

import com.glowup.api.domain.AnaliseReposicao;
import com.glowup.api.domain.StatusReposicao;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AnaliseReposicaoRespostaDTO(
    Long id,
    Long pedidoId,
    Long clienteId,
    String cliente,
    Long produtoId,
    String produto,
    LocalDate dataCompra,
    Integer tempoMedioUsoDias,
    LocalDate dataEstimadaReposicao,
    BigDecimal quantidadeSugerida,
    StatusReposicao status
) {
    public static AnaliseReposicaoRespostaDTO de(AnaliseReposicao a) {
        return new AnaliseReposicaoRespostaDTO(
                a.getId(),
                a.getPedidoId(),
                a.getUsuario().getId(),
                a.getUsuario().getNome(),
                a.getProduto().getId(),
                a.getProduto().getNome(),
                a.getDataCompra(),
                a.getTempoMedioUsoDias(),
                a.getDataEstimadaReposicao(),
                a.getQuantidadeSugerida(),
                a.getStatus());
    }
}
