package com.glowup.api.service;

import com.glowup.api.domain.Devolucao;
import com.glowup.api.domain.ItemDevolucao;
import com.glowup.api.domain.ItemKit;
import com.glowup.api.domain.ItemPedido;
import com.glowup.api.domain.Kit;
import com.glowup.api.domain.Pedido;
import com.glowup.api.domain.Produto;
import com.glowup.api.domain.TipoKit;
import com.glowup.api.domain.TipoMovimentacao;
import com.glowup.api.repository.KitRepository;
import com.glowup.api.repository.EstoqueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/**
 * Devolve às prateleiras as unidades baixadas no checkout. Espelha exatamente a regra de baixa
 * do PedidoService (inclusive a identificação de kits), para que baixa e estorno sejam simétricos.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EstornoEstoqueService {

    private final EstoqueRepository estoqueRepository;
    private final KitRepository kitRepository;
    private final MovimentacaoEstoqueService movimentacaoEstoqueService;

    @Transactional(propagation = Propagation.MANDATORY)
    public void devolverItensAoEstoque(Pedido pedido) {
        if (pedido.getItens() == null) {
            return;
        }
        for (ItemPedido item : pedido.getItens()) {
            devolverItem(item, item.getQuantidade(), pedido.getId());
        }
    }

    /** Devolução parcial: reintegra somente os itens e quantidades registrados na devolução. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void devolverItensDaDevolucao(Devolucao devolucao) {
        Long pedidoId = devolucao.getPedido().getId();
        for (ItemDevolucao itemDevolvido : devolucao.getItens()) {
            if (!Boolean.TRUE.equals(itemDevolvido.getProdutoLacrado())) {
                continue;
            }
            devolverItem(itemDevolvido.getItemPedido(), itemDevolvido.getQuantidade(), pedidoId);
        }
    }

    private void devolverItem(ItemPedido item, int quantidade, Long pedidoId) {
        Produto produto = item.getProduto();
        if (produto == null) {
            if (item.getProdutoIdPersistido() == null) {
                log.warn("Item {} sem identificador de produto: estoque não devolvido.", item.getId());
                return;
            }
            List<com.glowup.api.repository.ComponenteKitEstoqueProjecao> componentes =
                    kitRepository.buscarComponentesParaEstorno(item.getProdutoIdPersistido());
            if (!componentes.isEmpty()) {
                for (var componente : componentes) {
                    devolverUnidadesPorId(componente.getProdutoId(), componente.getQuantidade() * quantidade, pedidoId);
                }
            } else {
                devolverUnidadesPorId(item.getProdutoIdPersistido(), quantidade, pedidoId);
            }
            return;
        }

        Kit kit = kitRepository.findById(produto.getId()).orElse(null);
        if (kit != null && kit.getTipo() == TipoKit.MANUAL) {
            devolverComponentesDoKit(kit, quantidade, pedidoId);
        } else {
            devolverUnidades(produto, quantidade, pedidoId);
        }
    }

    private void devolverComponentesDoKit(Kit kit, int quantidadeDeKits, Long pedidoId) {
        for (ItemKit componente : kit.getItens()) {
            if (componente.getProduto() == null) {
                devolverUnidadesPorId(componente.getProdutoIdPersistido(),
                        componente.getQuantidade() * quantidadeDeKits, pedidoId);
            } else {
                devolverUnidades(componente.getProduto(), componente.getQuantidade() * quantidadeDeKits, pedidoId);
            }
        }
    }

    private void devolverUnidades(Produto produto, int quantidade, Long pedidoId) {
        // Produtos sob encomenda não possuem estoque físico (quantidadeEstoque nulo).
        if (produto == null || produto.getModalidade() == com.glowup.api.domain.ModalidadeProduto.SOB_ENCOMENDA) {
            return;
        }
        devolverUnidadesPorId(produto.getId(), quantidade, pedidoId);
    }

    private void devolverUnidadesPorId(Long produtoId, int quantidade, Long pedidoId) {
        if (produtoId == null || quantidade <= 0) return;
        int atualizadas = estoqueRepository.adicionarSaldo(produtoId, quantidade, java.time.LocalDateTime.now());
        if (atualizadas == 0) {
            log.warn("Estoque físico do produto {} não encontrado durante estorno do pedido {}.", produtoId, pedidoId);
            return;
        }
        movimentacaoEstoqueService.registrarPorProdutoId(produtoId, TipoMovimentacao.REINTEGRACAO, quantidade, pedidoId,
                "Estoque devolvido às prateleiras (cancelamento, rejeição, expiração ou devolução lacrada)");
    }
}
