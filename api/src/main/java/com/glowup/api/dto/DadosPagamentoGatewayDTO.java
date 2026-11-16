package com.glowup.api.dto;

import java.math.BigDecimal;

public record DadosPagamentoGatewayDTO(
    String id,
    String status,
    String statusDetalhe,
    BigDecimal valorTransacao,
    BigDecimal valorTotalPago,
    BigDecimal valorLiquido,
    Integer parcelas,
    String pixCopiaECola,
    String pixQrCodeBase64,
    String pixLinkTicket,
    String referenciaExterna
) {}
