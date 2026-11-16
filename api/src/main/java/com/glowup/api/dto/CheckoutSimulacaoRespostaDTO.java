package com.glowup.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record CheckoutSimulacaoRespostaDTO(List<CheckoutSimulacaoItemDTO> itens, BigDecimal subtotal,
        BigDecimal descontoCupom, BigDecimal valorFrete, BigDecimal valorTotal, Integer prazoDias,
        String codigoCupom) {}
