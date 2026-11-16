package com.glowup.api.service;

import com.glowup.api.domain.Cupom;
import com.glowup.api.domain.ItemCarrinho;
import com.glowup.api.domain.ModalidadeProduto;
import com.glowup.api.domain.Pedido;
import com.glowup.api.domain.Produto;
import com.glowup.api.domain.StatusPedido;
import com.glowup.api.domain.TipoEntrega;
import com.glowup.api.dto.CheckoutRequisicaoDTO;
import com.glowup.api.dto.ViaCepResponseDTO;
import com.glowup.api.exception.ConflitoDeEstadoException;
import com.glowup.api.model.Usuario;
import com.glowup.api.repository.EnderecoRepository;
import com.glowup.api.repository.EstoqueRepository;
import com.glowup.api.repository.PedidoEnderecoRepository;
import com.glowup.api.repository.PedidoRepository;
import com.glowup.api.repository.TermoAceiteRepository;
import com.glowup.api.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PedidoServiceTest {

    private PedidoRepository pedidoRepository;
    private PedidoEnderecoRepository pedidoEnderecoRepository;
    private EnderecoRepository enderecoRepository;
    private EstoqueRepository estoqueRepository;
    private CarrinhoService carrinhoService;
    private CupomService cupomService;
    private MovimentacaoEstoqueService movimentacaoEstoqueService;
    private TermoAceiteRepository termoAceiteRepository;
    private UsuarioRepository usuarioRepository;
    private PlatformTransactionManager transactionManager;
    
    private PedidoService pedidoService;
    private Usuario cliente;

    // Constante para ser usada nos testes
    private static final String CHAVE_TESTE = "chave-123";

    @BeforeEach
    void preparar() {
        pedidoRepository = mock(PedidoRepository.class);
        pedidoEnderecoRepository = mock(PedidoEnderecoRepository.class);
        enderecoRepository = mock(EnderecoRepository.class);
        estoqueRepository = mock(EstoqueRepository.class);
        carrinhoService = mock(CarrinhoService.class);
        cupomService = mock(CupomService.class);
        movimentacaoEstoqueService = mock(MovimentacaoEstoqueService.class);
        termoAceiteRepository = mock(TermoAceiteRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());

        pedidoService = new PedidoService(
                pedidoRepository, 
                pedidoEnderecoRepository, 
                enderecoRepository,
                estoqueRepository, 
                carrinhoService, 
                cupomService, 
                logisticaOffline(),
                mock(EstoqueDisponibilidadeService.class),
                movimentacaoEstoqueService,
                termoAceiteRepository,
                usuarioRepository,
                transactionManager
        );

        cliente = Usuario.builder().id(7L).nome("Ana").dataNascimento(LocalDate.now().minusYears(30)).build();

        // Configura o mock para retornar vazio quando buscar pela chave de idempotência
        when(pedidoRepository.findByClienteIdAndIdempotencyKey(anyLong(), any(String.class))).thenReturn(Optional.empty());

        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(invocacao -> {
            Pedido pedido = invocacao.getArgument(0);
            pedido.setId(99L);
            return pedido;
        });
        when(estoqueRepository.baixarSaldo(anyLong(), anyInt(), any())).thenReturn(1);
    }

    /** ViaCEP simulado como indisponível: o teste não depende da internet nem do cadastro real dos Correios. */
    private static LogisticaService logisticaOffline() {
        LogisticaService logistica = spy(new LogisticaService());
        ViaCepResponseDTO offline = new ViaCepResponseDTO();
        offline.setErroConexao(true);
        doReturn(offline).when(logistica).buscarCepComFallback(any());
        return logistica;
    }

    private Produto produto(long id, String preco, ModalidadeProduto modalidade) {
        Produto produto = new Produto();
        produto.setId(id);
        produto.setNome("Produto " + id);
        produto.setPrecoBase(new BigDecimal(preco));
        produto.setCustoFabrica(new BigDecimal("20.00"));
        produto.setModalidade(modalidade);
        return produto;
    }

    private void carrinhoComItem(Produto produto, int quantidade) {
        ItemCarrinho item = new ItemCarrinho();
        item.setProduto(produto);
        item.setQuantidade(quantidade);
        item.setPrecoUnitario(produto.getPrecoBase());
        List<ItemCarrinho> itens = new ArrayList<>();
        itens.add(item);
        when(carrinhoService.listarItensDoCarrinhoAtivo(7L)).thenReturn(itens);
    }

    private CheckoutRequisicaoDTO domicilio(String bairro, String uf, String cupom) {
        // Removido o "tkn" daqui
        return new CheckoutRequisicaoDTO(true, "1.0", TipoEntrega.DOMICILIO, null, "Brasil", "71590-000", "Quadra 1",
                "10", null, bairro, "Brasília", uf, cupom);
    }

    @Test
    @DisplayName("Calcula subtotal, frete de R$ 10,00 em Itapoã e total no servidor, ignorando qualquer valor do cliente")
    void deveCalcularTotaisNoServidor() {
        carrinhoComItem(produto(1, "50.00", ModalidadeProduto.PRONTA_ENTREGA), 2);

        Pedido pedido = pedidoService.finalizarCheckout(cliente, domicilio("Itapoã", "DF", null), CHAVE_TESTE);

        assertEquals(new BigDecimal("100.00"), pedido.getSubtotal());
        assertEquals(new BigDecimal("10.00"), pedido.getValorFrete());
        assertEquals(new BigDecimal("110.00"), pedido.getValorTotal());
        assertEquals(StatusPedido.AGUARDANDO_PAGAMENTO, pedido.getStatus());
        assertEquals(TipoEntrega.DOMICILIO, pedido.getTipoEntrega());
        assertEquals(7, pedido.getPrazoDias());
        assertEquals(7L, pedido.getCliente().getId());
        assertTrue(pedido.getItens().stream().allMatch(item -> item.getPedido() == pedido));
        verify(estoqueRepository).baixarSaldo(eq(1L), eq(2), any());
        verify(pedidoEnderecoRepository).save(any());
    }

    @Test
    @DisplayName("Mesma chave da mesma cliente devolve o pedido já criado, sem nova baixa de estoque")
    void deveReaproveitarPedidoDaMesmaChave() {
        Pedido existente = new Pedido();
        existente.setId(55L);
        when(pedidoRepository.findByClienteIdAndIdempotencyKey(7L, CHAVE_TESTE)).thenReturn(Optional.of(existente));

        Pedido resultado = pedidoService.finalizarCheckout(cliente, domicilio("Itapoã", "DF", null), CHAVE_TESTE);

        assertEquals(55L, resultado.getId());
        verify(usuarioRepository, never()).bloquearParaCheckout(7L);
        verify(estoqueRepository, never()).baixarSaldo(anyLong(), anyInt(), any());
        verify(pedidoRepository, never()).save(any(Pedido.class));
    }

    @Test
    @DisplayName("Retirada grátis força o frete a R$ 0,00 e dispensa endereço")
    void retiradaNaoCobraFrete() {
        carrinhoComItem(produto(1, "50.00", ModalidadeProduto.PRONTA_ENTREGA), 1);
        
        // Removido o "tkn" daqui
        CheckoutRequisicaoDTO retirada = new CheckoutRequisicaoDTO(true, "1.0", TipoEntrega.RETIRADA, null, null, null, null,
                null, null, null, null, null, null);

        Pedido pedido = pedidoService.finalizarCheckout(cliente, retirada, CHAVE_TESTE);

        assertEquals(new BigDecimal("0.00"), pedido.getValorFrete());
        assertEquals(new BigDecimal("50.00"), pedido.getValorTotal());
        assertEquals(TipoEntrega.RETIRADA, pedido.getTipoEntrega());
    }

    @Test
    @DisplayName("Entrega fora de Itapoã/Paranoá é rejeitada antes de mexer no estoque")
    void deveRejeitarRegiaoNaoAtendida() {
        carrinhoComItem(produto(1, "50.00", ModalidadeProduto.PRONTA_ENTREGA), 1);

        assertThrows(IllegalArgumentException.class,
                () -> pedidoService.finalizarCheckout(cliente, domicilio("Asa Norte", "DF", null), CHAVE_TESTE));
        assertThrows(IllegalArgumentException.class,
                () -> pedidoService.finalizarCheckout(cliente, domicilio("Itapoã", "GO", null), CHAVE_TESTE));
        verify(estoqueRepository, never()).baixarSaldo(anyLong(), anyInt(), any());
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Domicílio sem endereço completo é rejeitado")
    void deveExigirEnderecoCompleto() {
        carrinhoComItem(produto(1, "50.00", ModalidadeProduto.PRONTA_ENTREGA), 1);
        
        // Removido o "tkn" daqui
        CheckoutRequisicaoDTO incompleto = new CheckoutRequisicaoDTO(true, "1.0", TipoEntrega.DOMICILIO, null, null, "71590000", null,
                null, null, "Itapoã", null, "DF", null);

        assertThrows(IllegalArgumentException.class, () -> pedidoService.finalizarCheckout(cliente, incompleto, CHAVE_TESTE));
    }

    @Test
    @DisplayName("Menor de 18 anos não finaliza compra")
    void deveBloquearMenorDeIdade() {
        Usuario menor = Usuario.builder().id(8L).nome("Jovem").dataNascimento(LocalDate.now().minusYears(17)).build();

        assertThrows(IllegalArgumentException.class,
                () -> pedidoService.finalizarCheckout(menor, domicilio("Itapoã", "DF", null), CHAVE_TESTE));
    }

    @Test
    @DisplayName("Carrinho vazio não gera pedido")
    void deveRejeitarCarrinhoVazio() {
        when(carrinhoService.listarItensDoCarrinhoAtivo(7L)).thenReturn(new ArrayList<>());

        assertThrows(IllegalArgumentException.class,
                () -> pedidoService.finalizarCheckout(cliente, domicilio("Itapoã", "DF", null), CHAVE_TESTE));
    }

    @Test
    @DisplayName("Estoque insuficiente (baixa atômica sem linhas) bloqueia o pedido com conflito")
    void deveBloquearEstoqueInsuficiente() {
        carrinhoComItem(produto(1, "50.00", ModalidadeProduto.PRONTA_ENTREGA), 3);
        when(estoqueRepository.baixarSaldo(anyLong(), anyInt(), any())).thenReturn(0);

        assertThrows(ConflitoDeEstadoException.class,
                () -> pedidoService.finalizarCheckout(cliente, domicilio("Paranoá", "DF", null), CHAVE_TESTE));
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Produto Sob Encomenda não tem baixa de estoque")
    void sobEncomendaNaoBaixaEstoque() {
        carrinhoComItem(produto(2, "80.00", ModalidadeProduto.SOB_ENCOMENDA), 4);

        Pedido pedido = pedidoService.finalizarCheckout(cliente, domicilio("Itapoã", "DF", null), CHAVE_TESTE);

        assertEquals(new BigDecimal("320.00"), pedido.getSubtotal());
        verify(estoqueRepository, never()).baixarSaldo(anyLong(), anyInt(), any());
    }

    @Test
    @DisplayName("Cupom validado pelo servidor abate o subtotal antes do frete")
    void deveAplicarCupom() {
        carrinhoComItem(produto(1, "50.00", ModalidadeProduto.PRONTA_ENTREGA), 2);
        Cupom cupom = new Cupom();
        cupom.setId(3L);
        when(cupomService.validarEConsumir(eq("GLOW10"), any()))
                .thenReturn(new CupomService.DescontoAplicado(cupom, new BigDecimal("10.00")));

        Pedido pedido = pedidoService.finalizarCheckout(cliente, domicilio("Itapoã", "DF", "GLOW10"), CHAVE_TESTE);

        assertEquals(new BigDecimal("10.00"), pedido.getDescontoCupom());
        assertEquals(new BigDecimal("100.00"), pedido.getValorTotal());
        assertEquals(cupom, pedido.getCupom());
    }
}