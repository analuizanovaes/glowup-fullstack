package com.glowup.api.service;

import com.glowup.api.domain.MovimentacaoEstoque;
import com.glowup.api.domain.Estoque;
import com.glowup.api.domain.Produto;
import com.glowup.api.domain.TipoMovimentacao;
import com.glowup.api.repository.MovimentacaoEstoqueRepository;
import com.glowup.api.repository.EstoqueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registra a trilha de auditoria do estoque físico (DC05). Produtos Sob Encomenda não têm movimentação. */
@Service
@RequiredArgsConstructor
public class MovimentacaoEstoqueService {

    private final MovimentacaoEstoqueRepository movimentacaoRepository;
    private final EstoqueRepository estoqueRepository;

    @Transactional
    public void registrar(Produto produto, TipoMovimentacao tipo, int quantidade, Long pedidoId, String observacao) {
        if (produto == null || produto.getEstoque() == null || quantidade <= 0) {
            return;
        }
        registrar(produto.getEstoque(), tipo, quantidade, pedidoId, observacao);
    }

    @Transactional
    public void registrarPorProdutoId(Long produtoId, TipoMovimentacao tipo, int quantidade,
                                      Long pedidoId, String observacao) {
        if (produtoId == null || quantidade <= 0) return;
        estoqueRepository.buscarIdPorProduto(produtoId).ifPresent(estoqueId -> {
            Estoque estoque = new Estoque();
            estoque.setId(estoqueId);
            registrar(estoque, tipo, quantidade, pedidoId, observacao);
        });
    }

    private void registrar(Estoque estoque, TipoMovimentacao tipo, int quantidade, Long pedidoId, String observacao) {
        movimentacaoRepository.save(MovimentacaoEstoque.builder()
                .estoque(estoque)
                .pedidoId(pedidoId)
                .tipo(tipo)
                .quantidade(quantidade)
                .observacao(observacao)
                .build());
    }
}
