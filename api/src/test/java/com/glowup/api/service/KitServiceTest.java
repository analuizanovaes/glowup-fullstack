package com.glowup.api.service;

import com.glowup.api.domain.Categoria;
import com.glowup.api.domain.ItemKit;
import com.glowup.api.domain.Kit;
import com.glowup.api.domain.ModalidadeProduto;
import com.glowup.api.domain.Produto;
import com.glowup.api.domain.TipoKit;
import com.glowup.api.dto.ItemKitRequisicaoDTO;
import com.glowup.api.dto.KitRequisicaoDTO;
import com.glowup.api.repository.CategoriaRepository;
import com.glowup.api.repository.KitRepository;
import com.glowup.api.repository.ProdutoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KitServiceTest {

    private KitRepository kitRepository;
    private ProdutoRepository produtoRepository;
    private KitService kitService;

    @BeforeEach
    void preparar() {
        kitRepository = mock(KitRepository.class);
        produtoRepository = mock(ProdutoRepository.class);
        CategoriaRepository categoriaRepository = mock(CategoriaRepository.class);
        kitService = new KitService(kitRepository, produtoRepository, categoriaRepository,
                mock(MovimentacaoEstoqueService.class));

        Categoria categoria = new Categoria();
        categoria.setNome("Perfumaria");
        when(categoriaRepository.findByNomeIgnoreCase("Perfumaria")).thenReturn(Optional.of(categoria));
        when(kitRepository.save(any(Kit.class))).thenAnswer(chamada -> chamada.getArgument(0));
    }

    private Produto produto(long id) {
        Produto produto = new Produto();
        produto.setId(id);
        return produto;
    }

    private ItemKit item(Kit kit, long produtoId, int quantidade) {
        ItemKit item = new ItemKit();
        item.setKit(kit);
        item.setProduto(produto(produtoId));
        item.setQuantidade(quantidade);
        return item;
    }

    private Kit kitManual() {
        Kit kit = new Kit();
        kit.setId(50L);
        kit.setTipo(TipoKit.MANUAL);
        kit.setModalidade(ModalidadeProduto.PRONTA_ENTREGA);
        kit.getItens().add(item(kit, 1L, 1));
        kit.getItens().add(item(kit, 2L, 1));
        when(kitRepository.findById(50L)).thenReturn(Optional.of(kit));
        return kit;
    }

    private KitRequisicaoDTO dto(TipoKit tipo, BigDecimal promocional, LocalDateTime inicio, LocalDateTime fim,
                                 List<ItemKitRequisicaoDTO> itens) {
        return new KitRequisicaoDTO("Kit Presente", "Desc", "Boticário", "Perfumaria", new BigDecimal("80.00"),
                new BigDecimal("150.00"), promocional, inicio, fim, ModalidadeProduto.PRONTA_ENTREGA, tipo, null, itens);
    }

    @Test
    @DisplayName("Edição troca, atualiza e remove componentes sem recriar os que continuam no kit")
    void deveAtualizarComponentesDoKitManual() {
        Kit kit = kitManual();
        when(produtoRepository.findById(3L)).thenReturn(Optional.of(produto(3L)));

        Kit atualizado = kitService.atualizar(50L, dto(TipoKit.MANUAL, null, null, null,
                List.of(new ItemKitRequisicaoDTO(1L, 3), new ItemKitRequisicaoDTO(3L, 2))));

        assertEquals(2, atualizado.getItens().size());
        assertEquals(3, atualizado.getItens().stream()
                .filter(i -> i.getProduto().getId() == 1L).findFirst().orElseThrow().getQuantidade());
        assertTrue(atualizado.getItens().stream().anyMatch(i -> i.getProduto().getId() == 3L));
        assertTrue(atualizado.getItens().stream().noneMatch(i -> i.getProduto().getId() == 2L));
        assertEquals("Kit Presente", kit.getNome());
    }

    @Test
    @DisplayName("O tipo do kit não pode mudar na edição")
    void naoDeveTrocarTipoNaEdicao() {
        kitManual();

        assertThrows(IllegalArgumentException.class, () -> kitService.atualizar(50L,
                dto(TipoKit.FABRICA, null, null, null, null)));
    }

    @Test
    @DisplayName("Datas de promoção sem preço promocional são recusadas")
    void naoDeveAceitarDatasSemPrecoPromocional() {
        assertThrows(IllegalArgumentException.class, () -> kitService.criar(dto(TipoKit.MANUAL, null, null,
                LocalDateTime.now().plusDays(5), List.of(new ItemKitRequisicaoDTO(1L, 1)))));
    }

    @Test
    @DisplayName("O fim da promoção deve ser posterior ao início")
    void naoDeveAceitarFimAntesDoInicio() {
        LocalDateTime inicio = LocalDateTime.now().plusDays(5);

        assertThrows(IllegalArgumentException.class, () -> kitService.criar(dto(TipoKit.MANUAL,
                new BigDecimal("100.00"), inicio, inicio.minusDays(1), List.of(new ItemKitRequisicaoDTO(1L, 1)))));
    }
}
