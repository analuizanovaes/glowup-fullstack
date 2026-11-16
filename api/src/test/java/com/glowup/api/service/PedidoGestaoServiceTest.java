package com.glowup.api.service;

import com.glowup.api.domain.Pedido;
import com.glowup.api.domain.StatusPedido;
import com.glowup.api.dto.LucroRealDTO;
import com.glowup.api.exception.ConflitoDeEstadoException;
import com.glowup.api.model.Usuario;
import com.glowup.api.repository.PagamentoRepository;
import com.glowup.api.repository.PedidoEnderecoRepository;
import com.glowup.api.repository.PedidoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PedidoGestaoServiceTest {

    @Mock
    private PedidoRepository pedidoRepository;
    @Mock
    private PedidoEnderecoRepository pedidoEnderecoRepository;
    @Mock
    private PagamentoRepository pagamentoRepository;
    @Mock
    private PedidoStatusService pedidoStatusService;
    @Mock
    private EstornoEstoqueService estornoEstoqueService;
    @Mock
    private CupomService cupomService;
    @Mock
    private RegistroFinanceiroService registroFinanceiroService;
    @Mock
    private MercadoPagoGateway gateway;

    @InjectMocks
    private PedidoGestaoService pedidoGestaoService;

    private Pedido simularPedidoComStatus(StatusPedido status) {
        Pedido pedido = new Pedido();
        pedido.setId(1L);
        pedido.setStatus(status);
        pedido.setCliente(Usuario.builder().id(7L).nome("Ana").email("ana@teste.com").build());
        when(pedidoRepository.buscarParaAtualizacao(1L)).thenReturn(Optional.of(pedido));
        return pedido;
    }

    @Test
    @DisplayName("Permite Pago -> Em Separação e registra a administradora como origem")
    void devePermitirTransicaoValida() {
        Pedido pedido = simularPedidoComStatus(StatusPedido.PAGO);
        when(registroFinanceiroService.consultarLucro(1L)).thenReturn(LucroRealDTO.indisponivel("teste"));

        pedidoGestaoService.atualizarStatus(1L, StatusPedido.EM_SEPARACAO, "admin@glowup.com");

        verify(pedidoStatusService).alterar(pedido, StatusPedido.EM_SEPARACAO, "ADMIN:admin@glowup.com");
    }

    @Test
    @DisplayName("Bloqueia Enviado -> Cancelado")
    void deveBloquearCancelamentoDePedidoEnviado() {
        simularPedidoComStatus(StatusPedido.ENVIADO);

        ConflitoDeEstadoException excecao = assertThrows(ConflitoDeEstadoException.class,
                () -> pedidoGestaoService.atualizarStatus(1L, StatusPedido.CANCELADO, "admin@glowup.com"));

        assertEquals("Transição de status inválida: ENVIADO -> CANCELADO.", excecao.getMessage());
        verify(pedidoStatusService, never()).alterar(any(), any(), anyString());
    }

    @Test
    @DisplayName("Bloqueia pular etapas e marcar como pago manualmente")
    void deveBloquearTransicoesForaDaSequencia() {
        simularPedidoComStatus(StatusPedido.PAGO);

        assertThrows(ConflitoDeEstadoException.class,
                () -> pedidoGestaoService.atualizarStatus(1L, StatusPedido.ENVIADO, "admin@glowup.com"));
        assertThrows(ConflitoDeEstadoException.class,
                () -> pedidoGestaoService.atualizarStatus(1L, StatusPedido.CANCELADO, "admin@glowup.com"));

        verify(pedidoStatusService, never()).alterar(any(), any(), anyString());
    }

    @Test
    @DisplayName("Pedidos entregues e cancelados não aceitam nenhuma transição")
    void deveTratarEntregueECanceladoComoEstadosFinais() {
        simularPedidoComStatus(StatusPedido.ENTREGUE);

        assertThrows(ConflitoDeEstadoException.class,
                () -> pedidoGestaoService.atualizarStatus(1L, StatusPedido.PAGO, "admin@glowup.com"));
        verify(pedidoStatusService, never()).alterar(any(), any(), anyString());
    }

    @Test
    @DisplayName("Endereço pendente de conferência (ViaCEP fora do ar) bloqueia a separação")
    void deveBloquearSeparacaoComEnderecoPendente() {
        Pedido pedido = simularPedidoComStatus(StatusPedido.PAGO);
        pedido.setConferenciaEnderecoPendente(true);

        assertThrows(ConflitoDeEstadoException.class,
                () -> pedidoGestaoService.atualizarStatus(1L, StatusPedido.EM_SEPARACAO, "admin@glowup.com"));

        verify(pedidoStatusService, never()).alterar(any(), any(), anyString());
    }

    @Test
    @DisplayName("Depois de conferir o endereço, a separação é liberada")
    void deveLiberarSeparacaoAposConferirEndereco() {
        Pedido pedido = simularPedidoComStatus(StatusPedido.PAGO);
        pedido.setConferenciaEnderecoPendente(true);
        when(registroFinanceiroService.consultarLucro(1L)).thenReturn(LucroRealDTO.indisponivel("teste"));

        pedidoGestaoService.marcarEnderecoConferido(1L, "admin@glowup.com");
        pedidoGestaoService.atualizarStatus(1L, StatusPedido.EM_SEPARACAO, "admin@glowup.com");

        verify(pedidoStatusService).alterar(pedido, StatusPedido.EM_SEPARACAO, "ADMIN:admin@glowup.com");
    }
}
