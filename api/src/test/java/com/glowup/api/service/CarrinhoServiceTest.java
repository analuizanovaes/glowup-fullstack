package com.glowup.api.service;

import com.glowup.api.domain.Carrinho;
import com.glowup.api.domain.ItemCarrinho;
import com.glowup.api.domain.Produto;
import com.glowup.api.domain.StatusCarrinho;
import com.glowup.api.dto.CarrinhoRespostaDTO;
import com.glowup.api.exception.ConflitoDeEstadoException;
import com.glowup.api.exception.RecursoNaoEncontradoException;
import com.glowup.api.repository.CarrinhoRepository;
import com.glowup.api.repository.ItemCarrinhoRepository;
import com.glowup.api.repository.ProdutoRepository;
import com.glowup.api.repository.PedidoRepository;
import com.glowup.api.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CarrinhoServiceTest {

    private CarrinhoRepository carrinhoRepository;
    private ItemCarrinhoRepository itemCarrinhoRepository;
    private ProdutoRepository produtoRepository;
    private EstoqueDisponibilidadeService disponibilidadeService;
    private CarrinhoService carrinhoService;
    private Produto batom;

    @BeforeEach
    void preparar() {
        carrinhoRepository = mock(CarrinhoRepository.class);
        itemCarrinhoRepository = mock(ItemCarrinhoRepository.class);
        produtoRepository = mock(ProdutoRepository.class);
        disponibilidadeService = mock(EstoqueDisponibilidadeService.class);
        UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
        PedidoRepository pedidoRepository = mock(PedidoRepository.class);
        carrinhoService = new CarrinhoService(carrinhoRepository, itemCarrinhoRepository, produtoRepository,
                disponibilidadeService, usuarioRepository, pedidoRepository);

        Carrinho carrinho = new Carrinho();
        carrinho.setId(10L);
        carrinho.setUsuarioId(1L);
        when(carrinhoRepository.findByUsuarioIdAndStatus(1L, StatusCarrinho.ATIVO)).thenReturn(Optional.of(carrinho));

        batom = new Produto();
        batom.setId(5L);
        batom.setNome("Batom");
        batom.setPrecoBase(new BigDecimal("30.00"));
        when(produtoRepository.findById(5L)).thenReturn(Optional.of(batom));
        when(itemCarrinhoRepository.buscarItem(10L, 5L)).thenReturn(Optional.empty());
        when(itemCarrinhoRepository.listarItens(10L)).thenReturn(new ArrayList<>());
    }

    @Test
    @DisplayName("Adiciona item novo com o preço vigente do catálogo")
    void deveAdicionarItem() {
        when(disponibilidadeService.calcular(batom)).thenReturn(10);

        carrinhoService.adicionarItem(1L, 5L, 2);

        verify(itemCarrinhoRepository).save(any(ItemCarrinho.class));
    }

    @Test
    @DisplayName("Bloqueia a adição quando passa do estoque disponível")
    void deveBloquearQuandoPassaDoEstoque() {
        when(disponibilidadeService.calcular(batom)).thenReturn(1);

        assertThrows(ConflitoDeEstadoException.class, () -> carrinhoService.adicionarItem(1L, 5L, 2));
        verify(itemCarrinhoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Produto inexistente ou inativo responde como não encontrado")
    void deveRejeitarProdutoInexistente() {
        when(produtoRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class, () -> carrinhoService.adicionarItem(1L, 404L, 1));
    }

    @Test
    @DisplayName("Consulta recalcula o subtotal e alerta quando o estoque mudou")
    void deveAlertarMudancaDeEstoque() {
        ItemCarrinho item = new ItemCarrinho();
        item.setProduto(batom);
        item.setQuantidade(3);
        item.setPrecoUnitario(new BigDecimal("30.00"));
        List<ItemCarrinho> itens = new ArrayList<>();
        itens.add(item);
        when(itemCarrinhoRepository.listarItens(10L)).thenReturn(itens);
        when(disponibilidadeService.calcular(batom)).thenReturn(1);

        CarrinhoRespostaDTO resposta = carrinhoService.consultar(1L);

        assertEquals(new BigDecimal("90.00"), resposta.subtotal());
        assertEquals(3, resposta.totalItens());
        assertFalse(resposta.podeFinalizar());
        assertFalse(resposta.alertas().isEmpty());
    }

    @Test
    @DisplayName("Carrinho com estoque ok pode finalizar e Sob Encomenda não expõe saldo")
    void devePermitirFinalizarComEstoqueOk() {
        ItemCarrinho item = new ItemCarrinho();
        item.setProduto(batom);
        item.setQuantidade(2);
        item.setPrecoUnitario(new BigDecimal("30.00"));
        List<ItemCarrinho> itens = new ArrayList<>();
        itens.add(item);
        when(itemCarrinhoRepository.listarItens(10L)).thenReturn(itens);
        when(disponibilidadeService.calcular(batom)).thenReturn(EstoqueDisponibilidadeService.ILIMITADO);

        CarrinhoRespostaDTO resposta = carrinhoService.consultar(1L);

        assertTrue(resposta.podeFinalizar());
        assertTrue(resposta.alertas().isEmpty());
        assertNull(resposta.itens().get(0).estoqueDisponivel());
    }

    @Test
    @DisplayName("Esvaziar atua somente no carrinho da própria cliente")
    void esvaziarUsaCarrinhoDaCliente() {
        carrinhoService.esvaziar(1L);

        verify(itemCarrinhoRepository).deleteByCarrinhoId(10L);
    }
}
