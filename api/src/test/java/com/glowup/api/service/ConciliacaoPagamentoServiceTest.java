package com.glowup.api.service;

import com.glowup.api.domain.MetodoPagamento;
import com.glowup.api.domain.Pagamento;
import com.glowup.api.domain.Pedido;
import com.glowup.api.domain.StatusPagamento;
import com.glowup.api.domain.StatusPedido;
import com.glowup.api.dto.DadosPagamentoGatewayDTO;
import com.glowup.api.exception.ConflitoDeEstadoException;
import com.glowup.api.repository.DevolucaoRepository;
import com.glowup.api.repository.PagamentoRepository;
import com.glowup.api.repository.PedidoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConciliacaoPagamentoServiceTest {

    @Mock
    private PedidoRepository pedidoRepository;
    @Mock
    private ParcelaService parcelaService;
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
    private ApplicationEventPublisher publicadorEventos;
    @Mock
    private MercadoPagoGateway gateway;
    @Mock
    private CrmAdminService crmAdminService;
    @Mock
    private DevolucaoRepository devolucaoRepository;

    @InjectMocks
    private ConciliacaoPagamentoService servico;

    private Pedido pedido(StatusPedido status) {
        Pedido pedido = new Pedido();
        pedido.setId(1L);
        pedido.setStatus(status);
        pedido.setValorTotal(new BigDecimal("100.00"));
        return pedido;
    }

    private Pagamento pagamento(StatusPagamento status) {
        return Pagamento.builder()
                .id(10L)
                .metodo(MetodoPagamento.PIX)
                .status(status)
                .valor(new BigDecimal("100.00"))
                .idGateway("mp-1")
                .build();
    }

    private DadosPagamentoGatewayDTO dados(String status, String valor, String referencia) {
        return new DadosPagamentoGatewayDTO("mp-1", status, null,
                valor == null ? null : new BigDecimal(valor), null, null, 1, null, null, null, referencia);
    }

    private void prepararPedidoEPagamento(Pedido pedido, Pagamento pagamento) {
        when(pedidoRepository.buscarParaAtualizacao(1L)).thenReturn(Optional.of(pedido));
        when(pagamentoRepository.findByPedidoId(1L)).thenReturn(Optional.of(pagamento));
    }

    @Test
    @DisplayName("Aprovado com valor e referência corretos: pedido vai para PAGO, registra o financeiro e publica o evento")
    void aprovaQuandoValorEReferenciaConferem() {
        Pedido pedido = pedido(StatusPedido.AGUARDANDO_PAGAMENTO);
        Pagamento pagamento = pagamento(StatusPagamento.PENDENTE);
        prepararPedidoEPagamento(pedido, pagamento);

        servico.aplicarResultado(1L, dados("approved", "100.00", "1"), "WEBHOOK:TESTE");

        assertEquals(StatusPagamento.APROVADO, pagamento.getStatus());
        verify(pedidoStatusService).alterar(pedido, StatusPedido.PAGO, "WEBHOOK:TESTE");
        verify(registroFinanceiroService).registrar(pedido, pagamento);
        verify(publicadorEventos).publishEvent(any(PagamentoAprovadoEvento.class));
    }

    @Test
    @DisplayName("Aprovado com valor diferente do pedido: não aprova, mantém pendente e avisa a administradora")
    void naoAprovaQuandoValorDiverge() {
        Pedido pedido = pedido(StatusPedido.AGUARDANDO_PAGAMENTO);
        Pagamento pagamento = pagamento(StatusPagamento.PENDENTE);
        prepararPedidoEPagamento(pedido, pagamento);

        servico.aplicarResultado(1L, dados("approved", "50.00", "1"), "WEBHOOK:TESTE");

        assertEquals(StatusPagamento.PENDENTE, pagamento.getStatus());
        verify(pedidoStatusService, never()).alterar(any(), eq(StatusPedido.PAGO), anyString());
        verify(registroFinanceiroService, never()).registrar(any(), any());
        verify(crmAdminService).criarNotificacao(eq("CONCILIACAO_PAGAMENTO"), anyString(), anyString(), eq(1L));
    }

    @Test
    @DisplayName("Aprovado com referência de outro pedido: não aprova e avisa a administradora")
    void naoAprovaQuandoReferenciaDiverge() {
        Pedido pedido = pedido(StatusPedido.AGUARDANDO_PAGAMENTO);
        Pagamento pagamento = pagamento(StatusPagamento.PENDENTE);
        prepararPedidoEPagamento(pedido, pagamento);

        servico.aplicarResultado(1L, dados("approved", "100.00", "999"), "WEBHOOK:TESTE");

        assertEquals(StatusPagamento.PENDENTE, pagamento.getStatus());
        verify(pedidoStatusService, never()).alterar(any(), eq(StatusPedido.PAGO), anyString());
        verify(crmAdminService).criarNotificacao(eq("CONCILIACAO_PAGAMENTO"), anyString(), anyString(), eq(1L));
    }

    @Test
    @DisplayName("Sem referência no gateway, vale só o valor (a conferência da referência é opcional)")
    void aprovaQuandoGatewayNaoInformaReferencia() {
        Pedido pedido = pedido(StatusPedido.AGUARDANDO_PAGAMENTO);
        Pagamento pagamento = pagamento(StatusPagamento.PENDENTE);
        prepararPedidoEPagamento(pedido, pagamento);

        servico.aplicarResultado(1L, dados("approved", "100.00", null), "WEBHOOK:TESTE");

        assertEquals(StatusPagamento.APROVADO, pagamento.getStatus());
    }

    @Test
    @DisplayName("Webhook repetido de um pagamento já aprovado não aprova duas vezes")
    void aprovacaoRepetidaEIdempotente() {
        Pedido pedido = pedido(StatusPedido.PAGO);
        Pagamento pagamento = pagamento(StatusPagamento.APROVADO);
        prepararPedidoEPagamento(pedido, pagamento);

        servico.aplicarResultado(1L, dados("approved", "100.00", "1"), "WEBHOOK:TESTE");

        verify(pedidoStatusService, never()).alterar(any(), any(), anyString());
        verify(registroFinanceiroService, never()).registrar(any(), any());
        verify(publicadorEventos, never()).publishEvent(any(PagamentoAprovadoEvento.class));
    }

    @Test
    @DisplayName("Pagamento rejeitado cancela o pedido, devolve o estoque e libera o cupom")
    void rejeitadoCancelaEDevolveEstoque() {
        Pedido pedido = pedido(StatusPedido.AGUARDANDO_PAGAMENTO);
        Pagamento pagamento = pagamento(StatusPagamento.PENDENTE);
        prepararPedidoEPagamento(pedido, pagamento);

        servico.aplicarResultado(1L, dados("rejected", "100.00", "1"), "CLIENTE:PAGAMENTO");

        assertEquals(StatusPagamento.REJEITADO, pagamento.getStatus());
        verify(estornoEstoqueService).devolverItensAoEstoque(pedido);
        verify(cupomService).liberarUso(pedido);
        verify(pedidoStatusService).alterar(pedido, StatusPedido.CANCELADO, "CLIENTE:PAGAMENTO");
    }

    @Test
    @DisplayName("Rejeição repetida não devolve o estoque duas vezes")
    void rejeicaoRepetidaNaoDevolveEstoqueDeNovo() {
        Pedido pedido = pedido(StatusPedido.CANCELADO);
        Pagamento pagamento = pagamento(StatusPagamento.REJEITADO);
        prepararPedidoEPagamento(pedido, pagamento);

        servico.aplicarResultado(1L, dados("rejected", "100.00", "1"), "WEBHOOK:TESTE");

        verify(estornoEstoqueService, never()).devolverItensAoEstoque(any());
        verify(pedidoStatusService, never()).alterar(any(), any(), anyString());
    }

    @Test
    @DisplayName("Aprovação que chega depois do pedido cancelado é estornada automaticamente")
    void aprovacaoAtrasadaEEstornada() {
        Pedido pedido = pedido(StatusPedido.CANCELADO);
        Pagamento pagamento = pagamento(StatusPagamento.PENDENTE);
        prepararPedidoEPagamento(pedido, pagamento);

        servico.aplicarResultado(1L, dados("approved", "100.00", "1"), "WEBHOOK:TESTE");

        verify(gateway).estornarPagamento(eq("mp-1"), eq("conciliacao-atrasada-mp-1"), isNull());
        assertEquals(StatusPagamento.ESTORNADO, pagamento.getStatus());
        verify(registroFinanceiroService).marcarComoEstornado(1L);
        verify(pedidoStatusService, never()).alterar(any(), eq(StatusPedido.PAGO), anyString());
        verify(crmAdminService).criarNotificacao(eq("CONCILIACAO_PAGAMENTO"), anyString(), anyString(), eq(1L));
    }

    @Test
    @DisplayName("Estorno/chargeback no gateway cancela o pedido pago e marca o financeiro como estornado")
    void estornoNoGatewayCancelaPedidoPago() {
        Pedido pedido = pedido(StatusPedido.PAGO);
        Pagamento pagamento = pagamento(StatusPagamento.APROVADO);
        prepararPedidoEPagamento(pedido, pagamento);

        servico.aplicarResultado(1L, dados("refunded", "100.00", "1"), "WEBHOOK:TESTE");

        assertEquals(StatusPagamento.ESTORNADO, pagamento.getStatus());
        verify(registroFinanceiroService).marcarComoEstornado(1L);
        verify(pedidoStatusService).alterar(pedido, StatusPedido.CANCELADO, "GATEWAY:ESTORNO");
        verify(crmAdminService).criarNotificacao(eq("CONCILIACAO_PAGAMENTO"), anyString(), anyString(), eq(1L));
    }

    @Test
    @DisplayName("Status pendente no gateway não altera o pedido")
    void pendenteNaoAlteraNada() {
        Pedido pedido = pedido(StatusPedido.AGUARDANDO_PAGAMENTO);
        Pagamento pagamento = pagamento(StatusPagamento.PENDENTE);
        prepararPedidoEPagamento(pedido, pagamento);

        servico.aplicarResultado(1L, dados("pending", "100.00", "1"), "WEBHOOK:TESTE");

        assertEquals(StatusPagamento.PENDENTE, pagamento.getStatus());
        verify(pedidoStatusService, never()).alterar(any(), any(), anyString());
    }

    @Test
    @DisplayName("Registrar pagamento duplicado para o mesmo pedido é recusado")
    void registrarNovoPagamentoRecusaDuplicado() {
        when(pedidoRepository.buscarParaAtualizacao(1L)).thenReturn(Optional.of(pedido(StatusPedido.AGUARDANDO_PAGAMENTO)));
        when(pagamentoRepository.existsByPedidoId(1L)).thenReturn(true);

        assertThrows(ConflitoDeEstadoException.class, () -> servico.registrarNovoPagamento(
                1L, MetodoPagamento.PIX, 1, LocalDateTime.now().plusMinutes(30), dados("pending", "100.00", "1")));
        verify(pagamentoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Registrar pagamento de pedido que não aguarda pagamento é recusado")
    void registrarNovoPagamentoRecusaPedidoForaDoEstado() {
        when(pedidoRepository.buscarParaAtualizacao(1L)).thenReturn(Optional.of(pedido(StatusPedido.CANCELADO)));
        when(pagamentoRepository.existsByPedidoId(1L)).thenReturn(false);

        assertThrows(ConflitoDeEstadoException.class, () -> servico.registrarNovoPagamento(
                1L, MetodoPagamento.PIX, 1, LocalDateTime.now().plusMinutes(30), dados("pending", "100.00", "1")));
    }

    @Test
    @DisplayName("Pagamento novo e pendente é gravado com o valor do pedido e o pedido continua aguardando")
    void registrarNovoPagamentoPendente() {
        Pedido pedido = pedido(StatusPedido.AGUARDANDO_PAGAMENTO);
        when(pedidoRepository.buscarParaAtualizacao(1L)).thenReturn(Optional.of(pedido));
        when(pagamentoRepository.existsByPedidoId(1L)).thenReturn(false);

        var resposta = servico.registrarNovoPagamento(
                1L, MetodoPagamento.PIX, 1, LocalDateTime.now().plusMinutes(30), dados("pending", "100.00", "1"));

        assertEquals(StatusPagamento.PENDENTE, resposta.status());
        assertEquals(new BigDecimal("100.00"), resposta.valor());
        verify(pedidoStatusService, never()).alterar(any(), any(), anyString());
    }

    @Test
    @DisplayName("Pedido sem pagamento expirado é cancelado e devolve estoque; com pagamento é ignorado")
    void cancelarPedidoSemPagamento() {
        Pedido semPagamento = pedido(StatusPedido.AGUARDANDO_PAGAMENTO);
        when(pedidoRepository.buscarParaAtualizacao(1L)).thenReturn(Optional.of(semPagamento));
        when(pagamentoRepository.existsByPedidoId(1L)).thenReturn(false);

        servico.cancelarPedidoSemPagamento(1L);

        verify(estornoEstoqueService).devolverItensAoEstoque(semPagamento);
        verify(cupomService).liberarUso(semPagamento);
        verify(pedidoStatusService).alterar(semPagamento, StatusPedido.CANCELADO, "SISTEMA:RESERVA_EXPIRADA");
    }

    @Test
    @DisplayName("Pedido que já tem pagamento não é cancelado pela varredura de reservas")
    void cancelarPedidoComPagamentoEIgnorado() {
        when(pedidoRepository.buscarParaAtualizacao(1L)).thenReturn(Optional.of(pedido(StatusPedido.AGUARDANDO_PAGAMENTO)));
        when(pagamentoRepository.existsByPedidoId(1L)).thenReturn(true);

        servico.cancelarPedidoSemPagamento(1L);

        verify(estornoEstoqueService, never()).devolverItensAoEstoque(any());
        verify(pedidoStatusService, never()).alterar(any(), any(), anyString());
    }
}
