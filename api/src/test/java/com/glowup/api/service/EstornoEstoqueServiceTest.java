package com.glowup.api.service;

import com.glowup.api.domain.ItemPedido;
import com.glowup.api.domain.ModalidadeProduto;
import com.glowup.api.domain.Pedido;
import com.glowup.api.domain.Produto;
import com.glowup.api.domain.TipoMovimentacao;
import com.glowup.api.repository.EstoqueRepository;
import com.glowup.api.repository.KitRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EstornoEstoqueServiceTest {

    @Mock
    private EstoqueRepository estoqueRepository;
    @Mock
    private KitRepository kitRepository;
    @Mock
    private MovimentacaoEstoqueService movimentacaoEstoqueService;

    @InjectMocks
    private EstornoEstoqueService servico;

    private Produto produto(long id, ModalidadeProduto modalidade) {
        Produto produto = new Produto();
        produto.setId(id);
        produto.setModalidade(modalidade);
        return produto;
    }

    private Pedido pedidoComItem(Produto produto, int quantidade) {
        ItemPedido item = new ItemPedido();
        item.setId(100L);
        item.setProduto(produto);
        item.setQuantidade(quantidade);
        Pedido pedido = new Pedido();
        pedido.setId(1L);
        pedido.setItens(List.of(item));
        return pedido;
    }

    @Test
    @DisplayName("Cancelamento devolve ao estoque a quantidade do item e registra a reintegração")
    void devolveUnidadesDeProdutoDeProntaEntrega() {
        Produto batom = produto(5L, ModalidadeProduto.PRONTA_ENTREGA);
        when(kitRepository.findById(5L)).thenReturn(Optional.empty());
        when(estoqueRepository.adicionarSaldo(eq(5L), eq(2), any(LocalDateTime.class))).thenReturn(1);

        servico.devolverItensAoEstoque(pedidoComItem(batom, 2));

        verify(estoqueRepository).adicionarSaldo(eq(5L), eq(2), any(LocalDateTime.class));
        verify(movimentacaoEstoqueService).registrarPorProdutoId(
                eq(5L), eq(TipoMovimentacao.REINTEGRACAO), eq(2), eq(1L), anyString());
    }

    @Test
    @DisplayName("Produto sob encomenda não tem estoque físico: nada é devolvido")
    void produtoSobEncomendaNaoDevolveEstoque() {
        Produto sobEncomenda = produto(6L, ModalidadeProduto.SOB_ENCOMENDA);
        when(kitRepository.findById(6L)).thenReturn(Optional.empty());

        servico.devolverItensAoEstoque(pedidoComItem(sobEncomenda, 3));

        verify(estoqueRepository, never()).adicionarSaldo(anyLong(), anyInt(), any());
        verifyNoInteractions(movimentacaoEstoqueService);
    }

    @Test
    @DisplayName("Sem linha de estoque no banco, não registra movimentação")
    void semEstoqueFisicoNaoRegistraMovimentacao() {
        Produto batom = produto(5L, ModalidadeProduto.PRONTA_ENTREGA);
        when(kitRepository.findById(5L)).thenReturn(Optional.empty());
        when(estoqueRepository.adicionarSaldo(eq(5L), eq(1), any(LocalDateTime.class))).thenReturn(0);

        servico.devolverItensAoEstoque(pedidoComItem(batom, 1));

        verifyNoInteractions(movimentacaoEstoqueService);
    }

    @Test
    @DisplayName("Pedido sem itens carregados não falha")
    void pedidoSemItensNaoFalha() {
        Pedido pedido = new Pedido();
        pedido.setId(1L);
        pedido.setItens(null);

        servico.devolverItensAoEstoque(pedido);

        verifyNoInteractions(estoqueRepository, movimentacaoEstoqueService);
    }
}
