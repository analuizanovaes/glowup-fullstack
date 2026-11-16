package com.glowup.api.dto;

import com.glowup.api.domain.Pedido;
import com.glowup.api.domain.StatusPedido;
import com.glowup.api.domain.TipoEntrega;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Pedido recém-criado: o front segue para POST /api/pagamentos com o pedidoId retornado. */
public record CheckoutRespostaDTO(
    Long pedidoId,
    LocalDateTime dataCriacao,
    StatusPedido status,
    TipoEntrega tipoEntrega,
    Integer prazoDias,
    BigDecimal subtotal,
    BigDecimal desconto,
    BigDecimal frete,
    BigDecimal total,
    String bairroEntrega,
    List<ItemPedidoClienteDTO> itens
) {
    public static CheckoutRespostaDTO de(Pedido pedido) {
        List<ItemPedidoClienteDTO> itens = pedido.getItens() == null
                ? List.of()
                : pedido.getItens().stream().map(ItemPedidoClienteDTO::de).toList();
        return new CheckoutRespostaDTO(
                pedido.getId(),
                pedido.getDataCriacao(),
                pedido.getStatus(),
                pedido.getTipoEntrega(),
                pedido.getPrazoDias(),
                pedido.getSubtotal(),
                pedido.getDescontoCupom(),
                pedido.getValorFrete(),
                pedido.getValorTotal(),
                pedido.getBairroEntrega(),
                itens);
    }
}
