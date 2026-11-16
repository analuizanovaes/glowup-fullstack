package com.glowup.api.dto;

import com.glowup.api.domain.MetodoPagamento;
import com.glowup.api.domain.Pagamento;
import com.glowup.api.domain.StatusPagamento;
import com.glowup.api.domain.StatusPedido;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PagamentoRespostaDTO(
    Long pagamentoId,
    String idGateway,
    Long pedidoId,
    MetodoPagamento metodo,
    StatusPagamento status,
    StatusPedido statusPedido,
    BigDecimal valor,
    Integer parcelas,
    BigDecimal juros,
    String pixCopiaECola,
    String pixQrCodeBase64,
    String pixLinkTicket,
    LocalDateTime pixExpiraEm
) {

    public static PagamentoRespostaDTO de(Pagamento pagamento, Long pedidoId, StatusPedido statusPedido,
                                          String pixQrCodeBase64) {
        return new PagamentoRespostaDTO(
                pagamento.getId(),
                pagamento.getIdGateway(),
                pedidoId,
                pagamento.getMetodo(),
                pagamento.getStatus(),
                statusPedido,
                pagamento.getValor(),
                pagamento.getParcelas(),
                pagamento.getJuros(),
                pagamento.getPixCopiaECola(),
                pixQrCodeBase64 != null ? pixQrCodeBase64 : pagamento.getPixQrCodeBase64(),
                pagamento.getPixLinkTicket(),
                pagamento.getPixExpiraEm());
    }
}
