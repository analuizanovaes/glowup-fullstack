package com.glowup.api.service;

import com.glowup.api.domain.*;
import com.glowup.api.dto.DadosPagamentoGatewayDTO;
import com.glowup.api.dto.PagamentoRequisicaoDTO;
import com.glowup.api.dto.PagamentoRespostaDTO;
import com.glowup.api.exception.ConflitoDeEstadoException;
import com.glowup.api.exception.RecursoNaoEncontradoException;
import com.glowup.api.model.Usuario;
import com.glowup.api.repository.PagamentoRepository;
import com.glowup.api.repository.PedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PagamentoServiceTest {

    private PedidoRepository pedidoRepository;
    private PagamentoRepository pagamentoRepository;
    private MercadoPagoGateway gateway;
    private ConciliacaoPagamentoService conciliacaoService;
    private CrmAdminService crmAdminService;
    private PagamentoService service;
    private Usuario cliente;
    private Pedido pedido;

    @BeforeEach
    void setUp() {
        pedidoRepository = mock(PedidoRepository.class);
        pagamentoRepository = mock(PagamentoRepository.class);
        gateway = mock(MercadoPagoGateway.class);
        conciliacaoService = mock(ConciliacaoPagamentoService.class);
        crmAdminService = mock(CrmAdminService.class);
        service = new PagamentoService(pedidoRepository, pagamentoRepository, gateway, conciliacaoService, crmAdminService);
        ReflectionTestUtils.setField(service, "urlNotificacao", "https://api.exemplo.test/webhook");
        ReflectionTestUtils.setField(service, "emailPagadorTeste", "");
        ReflectionTestUtils.setField(service, "expiracaoPixMinutos", 30L);
        cliente = new Usuario();
        cliente.setId(7L);
        cliente.setNome("Ana Cliente");
        cliente.setEmail("ana@example.test");
        pedido = new Pedido();
        pedido.setId(12L);
        pedido.setCliente(cliente);
        pedido.setSubtotal(new BigDecimal("100.00"));
        pedido.setDescontoCupom(new BigDecimal("10.00"));
        pedido.setValorFrete(new BigDecimal("5.00"));
        pedido.setValorTotal(new BigDecimal("95.00"));
        pedido.setStatus(StatusPedido.AGUARDANDO_PAGAMENTO);
    }

    @Test
    void criaPixComValorReferenciaExpiracaoEIdempotencia() {
        when(pedidoRepository.findByIdAndClienteId(12L, 7L)).thenReturn(Optional.of(pedido));
        when(pedidoRepository.somarItens(12L)).thenReturn(new BigDecimal("100.00"));
        when(pagamentoRepository.findByPedidoId(12L)).thenReturn(Optional.empty());
        DadosPagamentoGatewayDTO dados = new DadosPagamentoGatewayDTO("mp-1", "pending", "pending_waiting_transfer",
                new BigDecimal("95.00"), null, null, 1, "pix-code", "qr-base64", "https://ticket.test", "12");
        when(gateway.criarPagamento(anyMap(), eq("glowup-pagamento-12-PIX-1"))).thenReturn(dados);
        PagamentoRespostaDTO resposta = mock(PagamentoRespostaDTO.class);
        when(conciliacaoService.registrarNovoPagamento(eq(12L), eq(MetodoPagamento.PIX), eq(1), any(), eq(dados)))
                .thenReturn(resposta);

        assertSame(resposta, service.iniciarPagamento(cliente,
                new PagamentoRequisicaoDTO(12L, MetodoPagamento.PIX, null, null, null, null)));

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<Map<String, Object>> corpo = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(gateway).criarPagamento(corpo.capture(), eq("glowup-pagamento-12-PIX-1"));
        assertEquals(new BigDecimal("95.00"), corpo.getValue().get("transaction_amount"));
        assertEquals("12", corpo.getValue().get("external_reference"));
        assertEquals("pix", corpo.getValue().get("payment_method_id"));
        assertEquals("https://api.exemplo.test/webhook", corpo.getValue().get("notification_url"));
        @SuppressWarnings("unchecked") Map<String, Object> payer = (Map<String, Object>) corpo.getValue().get("payer");
        assertEquals("Ana", payer.get("first_name"));
        assertEquals("Cliente", payer.get("last_name"));
    }

    @Test
    void rejeitaPedidoDeOutraClienteSemChamarGateway() {
        when(pedidoRepository.findByIdAndClienteId(12L, 7L)).thenReturn(Optional.empty());
        assertThrows(RecursoNaoEncontradoException.class, () -> service.iniciarPagamento(cliente,
                new PagamentoRequisicaoDTO(12L, MetodoPagamento.PIX, null, null, null, null)));
        verifyNoInteractions(gateway);
    }

    @Test
    void bloqueiaPedidoComTotalInconsistente() {
        when(pedidoRepository.findByIdAndClienteId(12L, 7L)).thenReturn(Optional.of(pedido));
        when(pagamentoRepository.findByPedidoId(12L)).thenReturn(Optional.empty());
        when(pedidoRepository.somarItens(12L)).thenReturn(new BigDecimal("99.00"));
        assertThrows(ConflitoDeEstadoException.class, () -> service.iniciarPagamento(cliente,
                new PagamentoRequisicaoDTO(12L, MetodoPagamento.PIX, null, null, null, null)));
        verifyNoInteractions(gateway);
    }

    @Test
    void exigeTokenEBandeiraParaCartao() {
        when(pedidoRepository.findByIdAndClienteId(12L, 7L)).thenReturn(Optional.of(pedido));
        when(pagamentoRepository.findByPedidoId(12L)).thenReturn(Optional.empty());
        when(pedidoRepository.somarItens(12L)).thenReturn(new BigDecimal("100.00"));
        assertThrows(IllegalArgumentException.class, () -> service.iniciarPagamento(cliente,
                new PagamentoRequisicaoDTO(12L, MetodoPagamento.CARTAO, 1, null, "visa", null)));
        verifyNoInteractions(gateway);
    }

    @Test
    void reaproveitaPixPendenteDoMesmoPedidoSemCriarCobrancaDuplicada() {
        when(pedidoRepository.findByIdAndClienteId(12L, 7L)).thenReturn(Optional.of(pedido));
        Pagamento pagamento = Pagamento.builder().id(8L).idGateway("mp-existing").metodo(MetodoPagamento.PIX)
                .status(StatusPagamento.PENDENTE).valor(new BigDecimal("95.00")).parcelas(1)
                .pixCopiaECola("pix-code").pixQrCodeBase64("qr-persistido").build();
        when(pagamentoRepository.findByPedidoId(12L)).thenReturn(Optional.of(pagamento));

        PagamentoRespostaDTO resposta = service.iniciarPagamento(cliente,
                new PagamentoRequisicaoDTO(12L, MetodoPagamento.PIX, null, null, null, null));

        assertEquals("mp-existing", resposta.idGateway());
        assertEquals("pix-code", resposta.pixCopiaECola());
        assertEquals("qr-persistido", resposta.pixQrCodeBase64());
        verifyNoInteractions(gateway);
    }

    @Test
    void consultaRetornaQrCodePersistidoMesmoDepoisDeRecarregar() {
        when(pedidoRepository.findByIdAndClienteId(12L, 7L)).thenReturn(Optional.of(pedido));
        Pagamento pagamento = Pagamento.builder().id(8L).idGateway("mp-existing").metodo(MetodoPagamento.PIX)
                .status(StatusPagamento.PENDENTE).valor(new BigDecimal("95.00")).parcelas(1)
                .pixCopiaECola("pix-code").pixQrCodeBase64("qr-persistido").build();
        when(pagamentoRepository.findByPedidoId(12L)).thenReturn(Optional.of(pagamento));

        PagamentoRespostaDTO resposta = service.consultarPorPedido(cliente, 12L);

        assertEquals("pix-code", resposta.pixCopiaECola());
        assertEquals("qr-persistido", resposta.pixQrCodeBase64());
        verifyNoInteractions(gateway);
    }

    @Test
    void criaCartaoComTokenParcelasDocumentoEIdempotencia() {
        when(pedidoRepository.findByIdAndClienteId(12L, 7L)).thenReturn(Optional.of(pedido));
        when(pagamentoRepository.findByPedidoId(12L)).thenReturn(Optional.empty());
        when(pedidoRepository.somarItens(12L)).thenReturn(new BigDecimal("100.00"));
        DadosPagamentoGatewayDTO dados = new DadosPagamentoGatewayDTO("mp-card-1", "approved", "accredited",
                new BigDecimal("95.00"), null, null, 3, null, null, null, "12");
        when(gateway.criarPagamento(anyMap(), eq("glowup-pagamento-12-CARTAO-3"))).thenReturn(dados);
        PagamentoRespostaDTO resposta = mock(PagamentoRespostaDTO.class);
        when(conciliacaoService.registrarNovoPagamento(eq(12L), eq(MetodoPagamento.CARTAO), eq(3), any(), eq(dados)))
                .thenReturn(resposta);

        assertSame(resposta, service.iniciarPagamento(cliente,
                new PagamentoRequisicaoDTO(12L, MetodoPagamento.CARTAO, 3, "card-token", "master", "12345678909")));

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<Map<String, Object>> corpo = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(gateway).criarPagamento(corpo.capture(), eq("glowup-pagamento-12-CARTAO-3"));
        assertEquals("card-token", corpo.getValue().get("token"));
        assertEquals("master", corpo.getValue().get("payment_method_id"));
        assertEquals(3, corpo.getValue().get("installments"));
        assertEquals("GLOWUP", corpo.getValue().get("statement_descriptor"));
        @SuppressWarnings("unchecked") Map<String, String> identification = (Map<String, String>)
                ((Map<String, Object>) corpo.getValue().get("payer")).get("identification");
        assertEquals("12345678909", identification.get("number"));
    }

    @Test
    void rejeitaParcelamentoAcimaDoMaximoSemChamarGateway() {
        when(pedidoRepository.findByIdAndClienteId(12L, 7L)).thenReturn(Optional.of(pedido));

        assertThrows(IllegalArgumentException.class, () -> service.iniciarPagamento(cliente,
                new PagamentoRequisicaoDTO(12L, MetodoPagamento.CARTAO, 4, "card-token", "visa", null)));

        verifyNoInteractions(gateway);
    }
}
