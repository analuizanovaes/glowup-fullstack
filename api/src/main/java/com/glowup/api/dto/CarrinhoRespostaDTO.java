package com.glowup.api.dto;

import java.math.BigDecimal;
import java.util.List;

/** Sacola com subtotal recalculado em tempo real e alertas de mudança de estoque ou preço (RF06). */
public record CarrinhoRespostaDTO(
    Long carrinhoId,
    List<ItemCarrinhoRespostaDTO> itens,
    int totalItens,
    BigDecimal subtotal,
    boolean podeFinalizar,
    List<String> alertas
) {}
