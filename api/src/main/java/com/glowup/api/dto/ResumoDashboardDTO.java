package com.glowup.api.dto;

import com.glowup.api.domain.StatusPedido;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public record ResumoDashboardDTO(
    LocalDate inicio,
    LocalDate fim,
    long pedidosPagos,
    BigDecimal faturamentoBruto,
    BigDecimal custoTotal,
    BigDecimal taxasTotal,
    BigDecimal lucroRealTotal,
    long pedidosComDadosInsuficientes,
    Map<StatusPedido, Long> pedidosPorStatus
) {}
