package com.glowup.api.service;

import com.glowup.api.domain.AlertaValidade;
import com.glowup.api.domain.AnaliseReposicao;
import com.glowup.api.domain.Estoque;
import com.glowup.api.domain.ItemPedido;
import com.glowup.api.domain.ModalidadeProduto;
import com.glowup.api.domain.Pedido;
import com.glowup.api.domain.Produto;
import com.glowup.api.model.Usuario;
import com.glowup.api.repository.AlertaValidadeRepository;
import com.glowup.api.repository.AnaliseReposicaoRepository;
import com.glowup.api.repository.EstoqueRepository;
import com.glowup.api.repository.ProdutoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AutomacaoCronServiceTest {

    private ProdutoRepository produtoRepository;
    private EstoqueRepository estoqueRepository;
    private AlertaValidadeRepository alertaValidadeRepository;
    private AnaliseReposicaoRepository analiseReposicaoRepository;
    private CrmAdminService crmAdminService;
    private AutomacaoCronService cron;

    @BeforeEach
    void preparar() {
        produtoRepository = mock(ProdutoRepository.class);
        estoqueRepository = mock(EstoqueRepository.class);
        alertaValidadeRepository = mock(AlertaValidadeRepository.class);
        analiseReposicaoRepository = mock(AnaliseReposicaoRepository.class);
        crmAdminService = mock(CrmAdminService.class);
        cron = new AutomacaoCronService(produtoRepository, estoqueRepository, alertaValidadeRepository,
                analiseReposicaoRepository, crmAdminService);
    }

    private Estoque estoqueComValidade(long id, LocalDate validade, int quantidade) {
        Produto produto = new Produto();
        produto.setId(id);
        produto.setNome("Creme " + id);
        produto.setModalidade(ModalidadeProduto.PRONTA_ENTREGA);
        produto.setQuantidadeEstoque(quantidade);
        produto.setDataValidade(validade);
        Estoque estoque = produto.getEstoque();
        estoque.setId(id);
        estoque.setProduto(produto);
        return estoque;
    }

    @Test
    @DisplayName("RN08: promoção expirada volta ao preço base e limpa as datas")
    void deveReverterPromocaoExpirada() {
        Produto produto = new Produto();
        produto.setPrecoBase(new BigDecimal("100.00"));
        produto.setPrecoPromocional(new BigDecimal("80.00"));
        produto.setDataLimitePromocao(LocalDateTime.now().minusHours(1));
        when(produtoRepository.findByPrecoPromocionalIsNotNullAndDataLimitePromocaoBefore(any()))
                .thenReturn(List.of(produto));

        int revertidos = cron.reverterPromocoesExpiradas();

        assertEquals(1, revertidos);
        assertNull(produto.getPrecoPromocional());
        assertNull(produto.getDataLimitePromocao());
        assertEquals(new BigDecimal("100.00"), produto.obterPrecoVigente());
        verify(produtoRepository).save(produto);
    }

    @Test
    @DisplayName("Sem promoções expiradas a rotina não altera nada")
    void naoAlteraSemPromocaoExpirada() {
        when(produtoRepository.findByPrecoPromocionalIsNotNullAndDataLimitePromocaoBefore(any()))
                .thenReturn(List.of());

        assertEquals(0, cron.reverterPromocoesExpiradas());
        verify(produtoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Alerta de validade só para produtos vencendo em até 25 dias")
    void deveAlertarSomenteDentroDos25Dias() {
        Estoque proximo = estoqueComValidade(1, LocalDate.now().plusDays(10), 5);
        Estoque distante = estoqueComValidade(2, LocalDate.now().plusDays(60), 5);
        when(estoqueRepository.buscarComValidade()).thenReturn(List.of(proximo, distante));

        int criados = cron.verificarVencimentoProdutos();

        assertEquals(1, criados);
        ArgumentCaptor<AlertaValidade> captor = ArgumentCaptor.forClass(AlertaValidade.class);
        verify(alertaValidadeRepository).save(captor.capture());
        assertEquals(10, captor.getValue().getDiasRestantes());
        verify(crmAdminService).criarNotificacao(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Não duplica o alerta quando já existe um aberto para o mesmo estoque")
    void naoDuplicaAlertaAberto() {
        Estoque proximo = estoqueComValidade(1, LocalDate.now().plusDays(5), 5);
        when(estoqueRepository.buscarComValidade()).thenReturn(List.of(proximo));
        when(alertaValidadeRepository.existsByEstoqueIdAndDataPrevista(any(), any())).thenReturn(true);

        assertEquals(0, cron.verificarVencimentoProdutos());
        verify(alertaValidadeRepository, never()).save(any());
    }

    @Test
    @DisplayName("CRM sugere reposição quando data da compra + tempo médio de uso já passou")
    void deveSugerirReposicao() {
        Usuario cliente = Usuario.builder().id(3L).nome("Ana").build();
        Pedido pedido = new Pedido();
        pedido.setId(50L);
        pedido.setCliente(cliente);
        pedido.setDataCriacao(LocalDateTime.now().minusDays(40));
        Produto produto = new Produto();
        produto.setId(9L);
        produto.setNome("Hidratante");
        produto.setTempoMedioUsoDias(30);
        ItemPedido item = new ItemPedido();
        item.setPedido(pedido);
        item.setProduto(produto);
        item.setQuantidade(2);
        ItemPedido recente = new ItemPedido();
        Pedido pedidoRecente = new Pedido();
        pedidoRecente.setId(51L);
        pedidoRecente.setCliente(cliente);
        pedidoRecente.setDataCriacao(LocalDateTime.now().minusDays(5));
        recente.setPedido(pedidoRecente);
        recente.setProduto(produto);
        recente.setQuantidade(1);
        when(analiseReposicaoRepository.buscarItensSemAnalise(any())).thenReturn(List.of(item, recente));

        int criadas = cron.verificarSugestaoReposicaoCRM();

        assertEquals(1, criadas);
        ArgumentCaptor<AnaliseReposicao> captor = ArgumentCaptor.forClass(AnaliseReposicao.class);
        verify(analiseReposicaoRepository).save(captor.capture());
        assertEquals(50L, captor.getValue().getPedidoId());
        assertEquals(LocalDate.now().minusDays(40).plusDays(30), captor.getValue().getDataEstimadaReposicao());
        assertEquals(new BigDecimal("2"), captor.getValue().getQuantidadeSugerida());
    }
}
