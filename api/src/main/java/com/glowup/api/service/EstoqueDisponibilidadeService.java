package com.glowup.api.service;

import com.glowup.api.domain.Estoque;
import com.glowup.api.domain.ItemKit;
import com.glowup.api.domain.Kit;
import com.glowup.api.domain.ModalidadeProduto;
import com.glowup.api.domain.Produto;
import com.glowup.api.domain.TipoKit;
import org.springframework.stereotype.Service;

/**
 * Quantas unidades de um produto podem ser vendidas agora (RNF02). Sob Encomenda é ilimitado;
 * kit manual é limitado pelo componente mais escasso; kit de fábrica usa o próprio estoque.
 */
@Service
public class EstoqueDisponibilidadeService {

    public static final int ILIMITADO = Integer.MAX_VALUE;

    public int calcular(Produto produto) {
        if (produto instanceof Kit kit && kit.getTipo() == TipoKit.MANUAL) {
            int disponivel = ILIMITADO;
            for (ItemKit componente : kit.getItens()) {
                int unidades = unidadesFisicas(componente.getProduto());
                if (unidades != ILIMITADO) {
                    disponivel = Math.min(disponivel, unidades / Math.max(1, componente.getQuantidade()));
                }
            }
            return disponivel;
        }
        return unidadesFisicas(produto);
    }

    private int unidadesFisicas(Produto produto) {
        // @NotFound(IGNORE) deixa o componente nulo quando ele foi inativado (soft delete).
        // Para vitrine e carrinho, esse componente torna o kit indisponível em vez de derrubar a resposta.
        if (produto == null) {
            return 0;
        }
        if (produto.getModalidade() == ModalidadeProduto.SOB_ENCOMENDA) {
            return ILIMITADO;
        }
        Estoque estoque = produto.getEstoque();
        if (estoque == null) {
            return 0;
        }
        return Math.max(0, estoque.getQuantidade() - estoque.getQuantidadeReservada());
    }
}
