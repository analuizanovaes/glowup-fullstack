package com.glowup.api.dto;

import java.math.BigDecimal;

public record CheckoutSimulacaoItemDTO(Long produtoId, String produtoNome, Integer quantidade,
        BigDecimal precoUnitario, BigDecimal subtotal) {}
