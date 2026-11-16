package com.glowup.api.service;

import com.glowup.api.domain.Devolucao;
import com.glowup.api.domain.HistoricoStatusPedido;
import com.glowup.api.domain.ItemDevolucao;
import com.glowup.api.domain.ItemPedido;
import com.glowup.api.domain.Pedido;
import com.glowup.api.domain.StatusDevolucao;
import com.glowup.api.domain.StatusPedido;
import com.glowup.api.domain.Pagamento;
import com.glowup.api.domain.StatusPagamento;
import com.glowup.api.dto.AnaliseDevolucaoDTO;
import com.glowup.api.dto.DevolucaoRespostaDTO;
import com.glowup.api.dto.ItemLacreDTO;
import com.glowup.api.dto.ItemDevolucaoRequisicaoDTO;
import com.glowup.api.dto.SolicitacaoDevolucaoDTO;
import com.glowup.api.exception.ConflitoDeEstadoException;
import com.glowup.api.model.Usuario;
import com.glowup.api.repository.DevolucaoRepository;
import com.glowup.api.repository.HistoricoStatusPedidoRepository;
import com.glowup.api.repository.PagamentoRepository;
import com.glowup.api.repository.PedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DevolucaoServiceTest {

    @Mock
    private DevolucaoRepository devolucaoRepository;
    @Mock
    private PedidoRepository pedidoRepository;
    @Mock
    private PagamentoRepository pagamentoRepository;
    @Mock
    private HistoricoStatusPedidoRepository historicoRepository;
    @Mock
    private EstornoEstoqueService estornoEstoqueService;
    @Mock
    private RegistroFinanceiroService registroFinanceiroService;
    @Mock
    private MercadoPagoGateway gateway;
    @Mock
    private EstornoService estornoService;
    @Mock
    private ParcelaService parcelaService;
    @Mock
    private PlatformTransactionManager transactionManager;

    @InjectMocks
    private DevolucaoService devolucaoService;

    private final Usuario cliente = Usuario.builder().id(7L).nome("Ana").email("ana@teste.com").build();

    @BeforeEach
    void configurarPrazoLegal() {
        ReflectionTestUtils.setField(devolucaoService, "prazoDias", 7L);
        lenient().when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());
    }

    private Pedido simularPedido(StatusPedido status) {
        Pedido pedido = new Pedido();
        pedido.setId(10L);
        pedido.setStatus(status);
        pedido.setCliente(cliente);
        when(pedidoRepository.buscarParaAtualizacao(10L)).thenReturn(Optional.of(pedido));
        return pedido;
    }

    /** Pedido entregue com 1 item (id 100, 5 unidades a R$ 20,00 = R$ 100,00) e frete de R$ 10,00. */
    private Pedido simularPedidoEntregueComItem(BigDecimal desconto) {
        Pedido pedido = simularPedido(StatusPedido.ENTREGUE);
        ItemPedido item = new ItemPedido();
        item.setId(100L);
        item.setQuantidade(5);
        item.setPrecoUnitario(new BigDecimal("20.00"));
        item.setPedido(pedido);
        pedido.setItens(new ArrayList<>(List.of(item)));
        pedido.setSubtotal(new BigDecimal("100.00"));
        pedido.setDescontoCupom(desconto);
        pedido.setValorFrete(new BigDecimal("10.00"));
        pedido.setValorTotal(new BigDecimal("100.00").subtract(desconto).add(new BigDecimal("10.00")));
        simularEntregueHaDias(pedido, 1);
        lenient().when(devolucaoRepository.save(any(Devolucao.class))).thenAnswer(chamada -> chamada.getArgument(0));
        return pedido;
    }

    private void simularEntregueHaDias(Pedido pedido, long dias) {
        HistoricoStatusPedido entrega = HistoricoStatusPedido.builder()
                .pedido(pedido)
                .statusNovo(StatusPedido.ENTREGUE)
                .origem("ADMIN:teste")
                .ocorridoEm(LocalDateTime.now().minusDays(dias))
                .build();
        when(historicoRepository.findFirstByPedidoIdAndStatusNovoOrderByOcorridoEmDesc(10L, StatusPedido.ENTREGUE))
                .thenReturn(Optional.of(entrega));
    }

    private Devolucao simularDevolucao(StatusDevolucao status) {
        Pedido pedido = new Pedido();
        pedido.setId(10L);
        Devolucao devolucao = Devolucao.builder().id(5L).pedido(pedido).status(status).motivo("Troca comercial").build();
        when(devolucaoRepository.buscarParaAtualizacao(5L)).thenReturn(Optional.of(devolucao));
        return devolucao;
    }

    @Test
    @DisplayName("Só pedidos entregues podem ser devolvidos")
    void deveExigirPedidoEntregue() {
        simularPedido(StatusPedido.PAGO);

        IllegalArgumentException excecao = assertThrows(IllegalArgumentException.class,
                () -> devolucaoService.solicitar(cliente, 10L, new SolicitacaoDevolucaoDTO("Arrependimento", null)));

        assertEquals("A devolução só pode ser solicitada para pedidos entregues.", excecao.getMessage());
    }

    @Test
    @DisplayName("Recusa devolução solicitada depois de 7 dias da entrega")
    void deveRecusarForaDoPrazoLegal() {
        Pedido pedido = simularPedido(StatusPedido.ENTREGUE);
        simularEntregueHaDias(pedido, 8);

        IllegalArgumentException excecao = assertThrows(IllegalArgumentException.class,
                () -> devolucaoService.solicitar(cliente, 10L, new SolicitacaoDevolucaoDTO("Arrependimento", null)));

        assertEquals("O prazo de 7 dias para devolução expirou.", excecao.getMessage());
        verify(devolucaoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Aceita devolução dentro do prazo e abre como SOLICITADA")
    void deveAceitarDentroDoPrazoLegal() {
        simularPedidoEntregueComItem(BigDecimal.ZERO);

        DevolucaoRespostaDTO resposta = devolucaoService.solicitar(cliente, 10L, new SolicitacaoDevolucaoDTO(
                "Arrependimento", List.of(new ItemDevolucaoRequisicaoDTO(100L, 5))));

        assertEquals(StatusDevolucao.SOLICITADA, resposta.status());
    }

    @Test
    @DisplayName("Devolução de todos os itens estorna o total do pedido, com frete")
    void deveEstornarTotalQuandoDevolveTudo() {
        simularPedidoEntregueComItem(BigDecimal.ZERO);

        DevolucaoRespostaDTO resposta = devolucaoService.solicitar(cliente, 10L, new SolicitacaoDevolucaoDTO(
                "Arrependimento", List.of(new ItemDevolucaoRequisicaoDTO(100L, 5))));

        assertEquals(new BigDecimal("110.00"), resposta.valorEstornado());
    }

    @Test
    @DisplayName("Devolução parcial estorna só os itens devolvidos (1 de 5)")
    void deveCalcularEstornoParcial() {
        simularPedidoEntregueComItem(BigDecimal.ZERO);

        DevolucaoRespostaDTO resposta = devolucaoService.solicitar(cliente, 10L, new SolicitacaoDevolucaoDTO(
                "Arrependimento", List.of(new ItemDevolucaoRequisicaoDTO(100L, 1))));

        assertEquals(new BigDecimal("20.00"), resposta.valorEstornado());
    }

    @Test
    @DisplayName("Devolução parcial abate a fração proporcional do cupom")
    void deveAbaterCupomNoEstornoParcial() {
        // subtotal 100, cupom 10 (10%): 1 item de R$ 20,00 volta como R$ 18,00
        simularPedidoEntregueComItem(new BigDecimal("10.00"));

        DevolucaoRespostaDTO resposta = devolucaoService.solicitar(cliente, 10L, new SolicitacaoDevolucaoDTO(
                "Arrependimento", List.of(new ItemDevolucaoRequisicaoDTO(100L, 1))));

        assertEquals(new BigDecimal("18.00"), resposta.valorEstornado());
    }

    @Test
    @DisplayName("Mesmo item repetido na lista soma as quantidades e não passa do comprado")
    void naoDevePermitirItemRepetidoAlemDoComprado() {
        simularPedidoEntregueComItem(BigDecimal.ZERO);

        IllegalArgumentException excecao = assertThrows(IllegalArgumentException.class,
                () -> devolucaoService.solicitar(cliente, 10L, new SolicitacaoDevolucaoDTO("Arrependimento",
                        List.of(new ItemDevolucaoRequisicaoDTO(100L, 3), new ItemDevolucaoRequisicaoDTO(100L, 3)))));

        assertEquals("A quantidade informada é maior que a comprada no pedido.", excecao.getMessage());
        verify(devolucaoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Item de outro pedido é recusado")
    void naoDeveAceitarItemDeOutroPedido() {
        simularPedidoEntregueComItem(BigDecimal.ZERO);

        IllegalArgumentException excecao = assertThrows(IllegalArgumentException.class,
                () -> devolucaoService.solicitar(cliente, 10L, new SolicitacaoDevolucaoDTO("Arrependimento",
                        List.of(new ItemDevolucaoRequisicaoDTO(999L, 1)))));

        assertEquals("O item informado não pertence a este pedido.", excecao.getMessage());
    }

    private void simularDevolucaoRecebida(Pedido pedido, int quantidade, String valor) {
        Devolucao anterior = Devolucao.builder()
                .id(4L)
                .pedido(pedido)
                .status(StatusDevolucao.RECEBIDA)
                .produtoLacrado(true)
                .valorEstornado(new BigDecimal(valor))
                .itens(new ArrayList<>(List.of(ItemDevolucao.builder()
                        .itemPedido(pedido.getItens().get(0))
                        .quantidade(quantidade)
                        .produtoLacrado(true)
                        .build())))
                .build();
        when(devolucaoRepository.findByPedidoIdAndStatus(10L, StatusDevolucao.RECEBIDA)).thenReturn(List.of(anterior));
    }

    @Test
    @DisplayName("Depois de uma devolução parcial recebida, devolver o restante estorna o que falta, com frete")
    void deveEstornarOQueFaltaAoQuitarOPedido() {
        Pedido pedido = simularPedidoEntregueComItem(BigDecimal.ZERO);
        simularDevolucaoRecebida(pedido, 2, "40.00");

        DevolucaoRespostaDTO resposta = devolucaoService.solicitar(cliente, 10L, new SolicitacaoDevolucaoDTO(
                "Arrependimento", List.of(new ItemDevolucaoRequisicaoDTO(100L, 3))));

        assertEquals(new BigDecimal("70.00"), resposta.valorEstornado());
    }

    @Test
    @DisplayName("Depois de uma devolução recebida, nova devolução parcial calcula só os itens pedidos")
    void deveAceitarNovaDevolucaoParcialDepoisDeRecebida() {
        Pedido pedido = simularPedidoEntregueComItem(BigDecimal.ZERO);
        simularDevolucaoRecebida(pedido, 2, "40.00");

        DevolucaoRespostaDTO resposta = devolucaoService.solicitar(cliente, 10L, new SolicitacaoDevolucaoDTO(
                "Arrependimento", List.of(new ItemDevolucaoRequisicaoDTO(100L, 1))));

        assertEquals(new BigDecimal("20.00"), resposta.valorEstornado());
    }

    @Test
    @DisplayName("Não aceita devolver mais do que resta depois de uma devolução recebida")
    void naoDeveExcederORestanteDevolvivel() {
        Pedido pedido = simularPedidoEntregueComItem(BigDecimal.ZERO);
        simularDevolucaoRecebida(pedido, 2, "40.00");

        IllegalArgumentException excecao = assertThrows(IllegalArgumentException.class,
                () -> devolucaoService.solicitar(cliente, 10L, new SolicitacaoDevolucaoDTO("Arrependimento",
                        List.of(new ItemDevolucaoRequisicaoDTO(100L, 4)))));

        assertEquals("A quantidade informada é maior que a restante para devolução neste pedido.",
                excecao.getMessage());
    }

    @Test
    @DisplayName("Pedido com todos os itens já devolvidos não aceita nova devolução")
    void naoDeveAceitarDevolucaoDePedidoJaDevolvido() {
        Pedido pedido = simularPedidoEntregueComItem(BigDecimal.ZERO);
        simularDevolucaoRecebida(pedido, 5, "110.00");

        assertThrows(ConflitoDeEstadoException.class, () -> devolucaoService.solicitar(cliente, 10L,
                new SolicitacaoDevolucaoDTO("Arrependimento", List.of(new ItemDevolucaoRequisicaoDTO(100L, 1)))));
    }

    @Test
    @DisplayName("Produto lacrado: devolução aprovada")
    void deveAprovarProdutoLacrado() {
        simularDevolucao(StatusDevolucao.SOLICITADA);

        DevolucaoRespostaDTO resposta = devolucaoService.analisar(5L, new AnaliseDevolucaoDTO(true, "Lacre intacto"));

        assertEquals(StatusDevolucao.APROVADA, resposta.status());
    }

    @Test
    @DisplayName("Lacre violado: devolução rejeitada (RD03)")
    void deveRejeitarProdutoComLacreViolado() {
        simularDevolucao(StatusDevolucao.SOLICITADA);

        DevolucaoRespostaDTO resposta = devolucaoService.analisar(5L, new AnaliseDevolucaoDTO(false, "Lacre rompido"));

        assertEquals(StatusDevolucao.REJEITADA, resposta.status());
    }

    @Test
    @DisplayName("Arrependimento do art. 49 não pode ser recusado por lacre violado")
    void deveAprovarArrependimentoMesmoComLacreViolado() {
        Devolucao devolucao = simularDevolucao(StatusDevolucao.SOLICITADA);
        devolucao.setMotivo("Arrependimento (art. 49 do CDC)");
        devolucao.setArrependimento(true);
        devolucao.setValorEstornado(new BigDecimal("20.00"));

        DevolucaoRespostaDTO resposta = devolucaoService.analisar(5L,
                new AnaliseDevolucaoDTO(false, "Embalagem aberta"));

        assertEquals(StatusDevolucao.APROVADA, resposta.status());
        assertTrue(resposta.arrependimento());
        assertEquals(Boolean.FALSE, resposta.produtoLacrado());
        assertEquals(new BigDecimal("20.00"), resposta.valorEstornado());
    }

    @Test
    @DisplayName("Aprova apenas os itens lacrados em devolução parcial")
    void deveAprovarParcialmenteItensLacrados() {
        Devolucao devolucao = simularDevolucao(StatusDevolucao.SOLICITADA);
        Pedido pedido = devolucao.getPedido();
        pedido.setSubtotal(new BigDecimal("20.00"));
        pedido.setDescontoCupom(BigDecimal.ZERO);

        ItemPedido itemPedidoLacrado = new ItemPedido();
        itemPedidoLacrado.setId(101L);
        itemPedidoLacrado.setQuantidade(1);
        itemPedidoLacrado.setPrecoUnitario(new BigDecimal("10.00"));
        ItemPedido itemPedidoViolado = new ItemPedido();
        itemPedidoViolado.setId(102L);
        itemPedidoViolado.setQuantidade(1);
        itemPedidoViolado.setPrecoUnitario(new BigDecimal("10.00"));

        ItemDevolucao itemLacrado = ItemDevolucao.builder().id(201L).devolucao(devolucao)
                .itemPedido(itemPedidoLacrado).quantidade(1).build();
        ItemDevolucao itemViolado = ItemDevolucao.builder().id(202L).devolucao(devolucao)
                .itemPedido(itemPedidoViolado).quantidade(1).build();
        devolucao.setItens(new ArrayList<>(List.of(itemLacrado, itemViolado)));
        devolucao.setValorEstornado(new BigDecimal("20.00"));

        DevolucaoRespostaDTO resposta = devolucaoService.analisar(5L,
                new AnaliseDevolucaoDTO(null, "Um item com lacre violado", List.of(
                        new ItemLacreDTO(201L, true), new ItemLacreDTO(202L, false))));

        assertEquals(StatusDevolucao.APROVADA, resposta.status());
        assertEquals(Boolean.TRUE, devolucao.getProdutoLacrado());
        assertEquals(new BigDecimal("10.00"), devolucao.getValorEstornado());
        verify(devolucaoRepository).save(devolucao);
    }

    @Test
    @DisplayName("Rejeição de todos os itens sem lacre não persiste estorno zero")
    void deveDeixarEstornoNuloQuandoNenhumItemTemLacre() {
        Devolucao devolucao = simularDevolucao(StatusDevolucao.SOLICITADA);
        ItemPedido itemPedido = new ItemPedido();
        itemPedido.setId(101L);
        itemPedido.setQuantidade(1);
        itemPedido.setPrecoUnitario(new BigDecimal("10.00"));
        ItemDevolucao item = ItemDevolucao.builder().id(201L).devolucao(devolucao)
                .itemPedido(itemPedido).quantidade(1).build();
        devolucao.setItens(new ArrayList<>(List.of(item)));
        devolucao.setValorEstornado(new BigDecimal("10.00"));

        DevolucaoRespostaDTO resposta = devolucaoService.analisar(5L,
                new AnaliseDevolucaoDTO(null, "Lacre violado", List.of(new ItemLacreDTO(201L, false))));

        assertEquals(StatusDevolucao.REJEITADA, resposta.status());
        assertNull(resposta.valorEstornado());
    }

    @Test
    @DisplayName("Devolução rejeitada nunca devolve estoque nem estorna")
    void naoDeveReintegrarEstoqueDeDevolucaoRejeitada() {
        simularDevolucao(StatusDevolucao.REJEITADA);

        assertThrows(ConflitoDeEstadoException.class, () -> devolucaoService.confirmarRecebimento(5L));

        verify(estornoEstoqueService, never()).devolverItensAoEstoque(any());
        verify(gateway, never()).estornarPagamento(any(), any(), any());
    }

    @Test
    @DisplayName("Estorno parcial nulo nunca é enviado como estorno total ao gateway")
    void naoDeveEnviarEstornoParcialNuloAoGateway() {
        Devolucao devolucao = simularDevolucao(StatusDevolucao.APROVADA);
        Pedido pedido = new Pedido();
        pedido.setId(10L);
        ItemPedido itemPedido = new ItemPedido();
        itemPedido.setId(100L);
        itemPedido.setQuantidade(2);
        itemPedido.setPrecoUnitario(new BigDecimal("10.00"));
        itemPedido.setPedido(pedido);
        pedido.setItens(new ArrayList<>(List.of(itemPedido)));
        ItemDevolucao item = ItemDevolucao.builder().id(201L).devolucao(devolucao)
                .itemPedido(itemPedido).quantidade(1).produtoLacrado(true).build();
        devolucao.setPedido(pedido);
        devolucao.setItens(new ArrayList<>(List.of(item)));
        devolucao.setValorEstornado(null);
        when(pedidoRepository.buscarParaAtualizacao(10L)).thenReturn(Optional.of(pedido));
        Pagamento pagamento = new Pagamento();
        pagamento.setStatus(StatusPagamento.APROVADO);
        pagamento.setIdGateway("mp-payment-10");
        when(pagamentoRepository.findByPedidoId(10L)).thenReturn(Optional.of(pagamento));
        when(devolucaoRepository.findByPedidoIdAndStatus(10L, StatusDevolucao.RECEBIDA)).thenReturn(List.of());

        assertThrows(ConflitoDeEstadoException.class, () -> devolucaoService.confirmarRecebimento(5L));
        verify(gateway, never()).estornarPagamento(any(), any(), any());
    }

    @Test
    @DisplayName("Uma devolução já analisada não pode ser analisada de novo")
    void naoDeveAnalisarDuasVezes() {
        simularDevolucao(StatusDevolucao.APROVADA);

        assertThrows(ConflitoDeEstadoException.class,
                () -> devolucaoService.analisar(5L, new AnaliseDevolucaoDTO(true, null)));
    }
}
