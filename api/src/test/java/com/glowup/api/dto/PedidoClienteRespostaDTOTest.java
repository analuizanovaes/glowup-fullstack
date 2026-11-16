package com.glowup.api.dto;

import com.glowup.api.domain.Pedido;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PedidoClienteRespostaDTOTest {

    @Test
    void limiteDevolucaoUsaOPrazoRecebidoDaConfiguracao() {
        Pedido pedido = new Pedido();
        LocalDateTime entrega = LocalDateTime.of(2026, 11, 1, 10, 0);

        PedidoClienteRespostaDTO resposta = PedidoClienteRespostaDTO.de(pedido, null, entrega, 10);

        assertEquals(entrega.plusDays(10), resposta.limiteSolicitacaoDevolucao());
    }

    @Test
    void semDataDeEntregaNaoHaPrazoDevolucao() {
        Pedido pedido = new Pedido();

        PedidoClienteRespostaDTO resposta = PedidoClienteRespostaDTO.de(pedido, null, null, 7);

        assertNull(resposta.limiteSolicitacaoDevolucao());
    }
}
