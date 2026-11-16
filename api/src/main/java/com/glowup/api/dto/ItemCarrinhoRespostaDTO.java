package com.glowup.api.dto;

import java.math.BigDecimal;

/** estoqueDisponivel é nulo quando o item é Sob Encomenda (sem controle físico). */
public record ItemCarrinhoRespostaDTO(
    Long produtoId,
    String nome,
    String imagemUrl,
    Integer quantidade,
    BigDecimal precoUnitario,
    BigDecimal subtotal,
    Integer estoqueDisponivel,
    boolean disponivel
) {}
