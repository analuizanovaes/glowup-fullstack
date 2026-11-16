package com.glowup.api.service;

import com.glowup.api.domain.Categoria;
import com.glowup.api.domain.ModalidadeProduto;
import com.glowup.api.domain.Produto;
import com.glowup.api.dto.ProdutoRequisicaoDTO;
import com.glowup.api.exception.ConflitoDeEstadoException;
import com.glowup.api.exception.RecursoNaoEncontradoException;
import com.glowup.api.repository.CategoriaRepository;
import com.glowup.api.repository.ProdutoRepository;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProdutoServiceTest {

    private ProdutoRepository produtoRepository;
    private CategoriaRepository categoriaRepository;
    private ProdutoService produtoService;
    private Categoria perfumaria;

    @BeforeEach
    void preparar() {
        produtoRepository = mock(ProdutoRepository.class);
        categoriaRepository = mock(CategoriaRepository.class);
        produtoService = new ProdutoService(produtoRepository, categoriaRepository,
                mock(MovimentacaoEstoqueService.class), mock(EstoqueAdminService.class));

        perfumaria = new Categoria();
        perfumaria.setNome("Perfumaria");
        when(categoriaRepository.findByNomeIgnoreCase("Perfumaria")).thenReturn(Optional.of(perfumaria));
        when(produtoRepository.save(any(Produto.class))).thenAnswer(invocacao -> invocacao.getArgument(0));
    }

    private Produto produto(String nome, String preco, Categoria categoria) {
        Produto produto = new Produto();
        produto.setNome(nome);
        produto.setPrecoBase(new BigDecimal(preco));
        produto.setCategoria(categoria);
        return produto;
    }

    private ProdutoRequisicaoDTO dto(ModalidadeProduto modalidade, Integer estoque, String categoria) {
        return new ProdutoRequisicaoDTO("Malbec", "Desc", "Boticário", categoria, new BigDecimal("80.00"),
                new BigDecimal("150.00"), null, null, null, modalidade, estoque, null, 90, null);
    }

    @Test
    @DisplayName("Filtra por nome e categoria sem diferenciar maiúsculas nem acentos")
    void deveFiltrarSemAcento() {
        Produto agua = produto("Água de Colônia", "100.00", perfumaria);
        when(produtoRepository.buscarComFiltrosPaginados(isNull(), eq("agua"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(agua)));
        when(produtoRepository.buscarComFiltrosPaginados(eq("perfumaria"), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(agua)));

        List<Produto> porNome = produtoService.listarProdutosPaginados(null, "agua", null,
                PageRequest.of(0, 50)).getContent();
        List<Produto> porCategoria = produtoService.listarProdutosPaginados("PERFUMARIA", null, null,
                PageRequest.of(0, 50)).getContent();

        assertEquals(1, porNome.size());
        assertEquals("Água de Colônia", porNome.get(0).getNome());
        assertEquals(1, porCategoria.size());
        verify(produtoRepository).buscarComFiltrosPaginados(isNull(), eq("agua"), any(Pageable.class));
        verify(produtoRepository).buscarComFiltrosPaginados(eq("perfumaria"), isNull(), any(Pageable.class));
    }

    @Test
    @DisplayName("Ordena por preço crescente e decrescente")
    void deveOrdenarPorPreco() {
        Produto produtoB = produto("B", "50.00", perfumaria);
        Produto produtoA = produto("A", "10.00", perfumaria);
        Produto produtoC = produto("C", "30.00", perfumaria);
        when(produtoRepository.buscarComFiltrosPaginados(isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(produtoA, produtoC, produtoB)))
                .thenReturn(new PageImpl<>(List.of(produtoB, produtoC, produtoA)));

        assertEquals("A", produtoService.listarProdutosPaginados(null, null, "preco_asc",
                PageRequest.of(0, 50)).getContent().get(0).getNome());
        assertEquals("B", produtoService.listarProdutosPaginados(null, null, "preco_desc",
                PageRequest.of(0, 50)).getContent().get(0).getNome());
        verify(produtoRepository, times(2)).buscarComFiltrosPaginados(isNull(), isNull(), any(Pageable.class));
    }

    @Test
    @DisplayName("Ordenação desconhecida é rejeitada")
    void deveRejeitarOrdemInvalida() {
        assertThrows(IllegalArgumentException.class, () -> produtoService.listarProdutosPaginados(null, null,
                "aleatorio", PageRequest.of(0, 50)));
    }

    @Test
    @DisplayName("Pronta-Entrega exige quantidade em estoque")
    void prontaEntregaExigeQuantidade() {
        assertThrows(IllegalArgumentException.class,
                () -> produtoService.criar(dto(ModalidadeProduto.PRONTA_ENTREGA, null, "Perfumaria")));
        verify(produtoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Sob Encomenda ignora a quantidade informada (sem controle físico)")
    void sobEncomendaIgnoraQuantidade() {
        Produto criado = produtoService.criar(dto(ModalidadeProduto.SOB_ENCOMENDA, 50, "Perfumaria"));

        assertNull(criado.getEstoque());
        assertEquals(ModalidadeProduto.SOB_ENCOMENDA, criado.getModalidade());
    }

    @Test
    @DisplayName("Pronta-Entrega cria o saldo físico informado")
    void prontaEntregaCriaEstoque() {
        Produto criado = produtoService.criar(dto(ModalidadeProduto.PRONTA_ENTREGA, 12, "Perfumaria"));

        assertEquals(12, criado.getQuantidadeEstoque());
    }

    @Test
    @DisplayName("Categoria inexistente é rejeitada")
    void deveRejeitarCategoriaInexistente() {
        when(categoriaRepository.findByNomeIgnoreCase("Outra")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> produtoService.criar(dto(ModalidadeProduto.SOB_ENCOMENDA, null, "Outra")));
    }

    @Test
    @DisplayName("Pronta-Entrega não pode voltar para Sob Encomenda na edição")
    void naoPermiteVoltarParaSobEncomenda() {
        Produto existente = produto("Malbec", "150.00", perfumaria);
        existente.setModalidade(ModalidadeProduto.PRONTA_ENTREGA);
        existente.setQuantidadeEstoque(3);
        when(produtoRepository.findById(1L)).thenReturn(Optional.of(existente));

        assertThrows(IllegalArgumentException.class,
                () -> produtoService.atualizar(1L, dto(ModalidadeProduto.SOB_ENCOMENDA, null, "Perfumaria")));
    }

    @Test
    @DisplayName("Sob Encomenda pode virar Pronta-Entrega informando o estoque")
    void permiteSairDeSobEncomendaParaProntaEntrega() {
        Produto existente = produto("Malbec", "150.00", perfumaria);
        existente.setModalidade(ModalidadeProduto.SOB_ENCOMENDA);
        when(produtoRepository.findById(1L)).thenReturn(Optional.of(existente));

        Produto atualizado = produtoService.atualizar(1L, dto(ModalidadeProduto.PRONTA_ENTREGA, 15, "Perfumaria"));

        assertEquals(ModalidadeProduto.PRONTA_ENTREGA, atualizado.getModalidade());
        assertEquals(15, atualizado.getQuantidadeEstoque());
    }

    @Test
    @DisplayName("Reativa um produto inativado")
    void reativaProdutoInativado() {
        when(produtoRepository.contarInativosPorId(7L)).thenReturn(1L);
        when(produtoRepository.reativarPorId(7L)).thenReturn(1);

        produtoService.reativarProduto(7L);

        verify(produtoRepository).reativarPorId(7L);
    }

    @Test
    @DisplayName("Reativar um id que não está inativo falha com não encontrado")
    void reativarQuemNaoEstaInativoFalha() {
        when(produtoRepository.contarInativosPorId(8L)).thenReturn(0L);

        assertThrows(RecursoNaoEncontradoException.class, () -> produtoService.reativarProduto(8L));
        verify(produtoRepository, never()).reativarPorId(8L);
    }

    @Test
    @DisplayName("Não reativa produto cuja categoria está inativa")
    void naoReativaComCategoriaInativa() {
        when(produtoRepository.contarInativosPorId(9L)).thenReturn(1L);
        when(produtoRepository.reativarPorId(9L)).thenReturn(0);

        assertThrows(ConflitoDeEstadoException.class, () -> produtoService.reativarProduto(9L));
    }
}
