package com.glowup.api.service;

import com.glowup.api.domain.Carrinho;
import com.glowup.api.domain.ItemCarrinho;
import com.glowup.api.domain.Produto;
import com.glowup.api.domain.StatusCarrinho;
import com.glowup.api.dto.CarrinhoRespostaDTO;
import com.glowup.api.dto.ItemCarrinhoRespostaDTO;
import com.glowup.api.exception.ConflitoDeEstadoException;
import com.glowup.api.exception.RecursoNaoEncontradoException;
import com.glowup.api.repository.CarrinhoRepository;
import com.glowup.api.repository.ItemCarrinhoRepository;
import com.glowup.api.repository.ProdutoRepository;
import com.glowup.api.repository.PedidoRepository;
import com.glowup.api.repository.UsuarioRepository;
import com.glowup.api.domain.StatusPedido;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Sacola persistida no servidor (RF06 / seções 6.23, 6.29, 6.33 e 6.38). Sempre atua sobre o carrinho
 * da cliente autenticada: não existe operação por ID de carrinho, o que impede mexer na sacola alheia.
 */
@Service
@RequiredArgsConstructor
public class CarrinhoService {

    private static final int QUANTIDADE_MAXIMA_POR_ITEM = 99;

    private final CarrinhoRepository carrinhoRepository;
    private final ItemCarrinhoRepository itemCarrinhoRepository;
    private final ProdutoRepository produtoRepository;
    private final EstoqueDisponibilidadeService disponibilidadeService;
    private final UsuarioRepository usuarioRepository;
    private final PedidoRepository pedidoRepository;

    public BigDecimal calcularSubtotal(List<ItemCarrinho> itens) {
        BigDecimal subtotal = BigDecimal.ZERO;

        for (ItemCarrinho item : itens) {
            BigDecimal preco = item.getProduto().obterPrecoVigente();
            subtotal = subtotal.add(preco.multiply(BigDecimal.valueOf(item.getQuantidade())));
        }
        return subtotal;
    }

    @Transactional
    public CarrinhoRespostaDTO consultar(Long usuarioId) {
        Carrinho carrinho = obterOuCriarCarrinho(usuarioId);
        return montarResposta(carrinho);
    }

    @Transactional
    public CarrinhoRespostaDTO adicionarItem(Long usuarioId, Long produtoId, int quantidade) {
        validarSemPedidoPendente(usuarioId);
        Produto produto = buscarProdutoAtivo(produtoId);
        Carrinho carrinho = obterOuCriarCarrinho(usuarioId);

        ItemCarrinho item = itemCarrinhoRepository.buscarItem(carrinho.getId(), produtoId).orElse(null);
        int novaQuantidade = (item == null ? 0 : item.getQuantidade()) + quantidade;
        validarQuantidade(produto, novaQuantidade);

        if (item == null) {
            item = new ItemCarrinho();
            item.setCarrinhoId(carrinho.getId());
            item.setProduto(produto);
        }
        item.setQuantidade(novaQuantidade);
        item.setPrecoUnitario(produto.obterPrecoVigente());
        itemCarrinhoRepository.save(item);

        return montarResposta(carrinho);
    }

    @Transactional
    public CarrinhoRespostaDTO alterarQuantidade(Long usuarioId, Long produtoId, int quantidade) {
        validarSemPedidoPendente(usuarioId);
        Produto produto = buscarProdutoAtivo(produtoId);
        Carrinho carrinho = obterOuCriarCarrinho(usuarioId);

        ItemCarrinho item = itemCarrinhoRepository.buscarItem(carrinho.getId(), produtoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Este produto não está no seu carrinho."));
        validarQuantidade(produto, quantidade);

        item.setQuantidade(quantidade);
        item.setPrecoUnitario(produto.obterPrecoVigente());
        itemCarrinhoRepository.save(item);

        return montarResposta(carrinho);
    }

    @Transactional
    public CarrinhoRespostaDTO removerItem(Long usuarioId, Long produtoId) {
        validarSemPedidoPendente(usuarioId);
        Carrinho carrinho = obterOuCriarCarrinho(usuarioId);
        itemCarrinhoRepository.removerItem(carrinho.getId(), produtoId);
        return montarResposta(carrinho);
    }

    /** Esvazia apenas a sacola da própria cliente (remove itens temporários, nunca registros de pedidos). */
    @Transactional
    public CarrinhoRespostaDTO esvaziar(Long usuarioId) {
        validarSemPedidoPendente(usuarioId);
        Carrinho carrinho = obterOuCriarCarrinho(usuarioId);
        itemCarrinhoRepository.deleteByCarrinhoId(carrinho.getId());
        return montarResposta(carrinho);
    }

    /** Mantido por compatibilidade interna; a API pública usa {@link #esvaziar(Long)}. */
    @Transactional
    public void limparCarrinhoCompleto(Long carrinhoId) {
        itemCarrinhoRepository.deleteByCarrinhoId(carrinhoId);
    }

    /** Itens do carrinho ativo (vazio se a cliente ainda não tem sacola). Usado pelo checkout. */
    @Transactional(readOnly = true)
    public List<ItemCarrinho> listarItensDoCarrinhoAtivo(Long usuarioId) {
        Carrinho carrinho = carrinhoRepository.findByUsuarioIdAndStatus(usuarioId, StatusCarrinho.ATIVO).orElse(null);
        if (carrinho == null) {
            return new ArrayList<>();
        }
        return itemCarrinhoRepository.listarItens(carrinho.getId());
    }

    /**
     * Após a aprovação do pagamento a sacola é concluída (RN06: só então deixa de ser reaproveitável).
     * Transação própria: roda depois do commit do pagamento e uma falha aqui não desfaz a venda.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void concluirCarrinho(Long usuarioId) {
        carrinhoRepository.findByUsuarioIdAndStatus(usuarioId, StatusCarrinho.ATIVO).ifPresent(carrinho -> {
            carrinho.setStatus(StatusCarrinho.CONVERTIDO);
            carrinhoRepository.save(carrinho);
        });
    }

    private Carrinho obterOuCriarCarrinho(Long usuarioId) {
        usuarioRepository.bloquearParaCheckout(usuarioId);
        Carrinho existente = carrinhoRepository.findByUsuarioIdAndStatus(usuarioId, StatusCarrinho.ATIVO).orElse(null);
        if (existente != null) {
            return existente;
        }
        Carrinho novo = new Carrinho();
        novo.setUsuarioId(usuarioId);
        return carrinhoRepository.save(novo);
    }

    private Produto buscarProdutoAtivo(Long produtoId) {
        // O Soft Delete (@SQLRestriction) já oculta produtos inativos: findById devolve vazio para eles.
        return produtoRepository.findById(produtoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto não encontrado ou indisponível."));
    }

    private void validarQuantidade(Produto produto, int quantidade) {
        if (quantidade < 1) {
            throw new IllegalArgumentException("A quantidade mínima é 1.");
        }
        if (quantidade > QUANTIDADE_MAXIMA_POR_ITEM) {
            throw new IllegalArgumentException("A quantidade máxima por item é " + QUANTIDADE_MAXIMA_POR_ITEM + ".");
        }
        int disponivel = disponibilidadeService.calcular(produto);
        if (quantidade > disponivel) {
            throw new ConflitoDeEstadoException(disponivel == 0
                    ? "O produto " + produto.getNome() + " está esgotado."
                    : "Estoque insuficiente para " + produto.getNome() + ": restam " + disponivel + " unidade(s).");
        }
    }

    private CarrinhoRespostaDTO montarResposta(Carrinho carrinho) {
        List<ItemCarrinho> itens = itemCarrinhoRepository.listarItens(carrinho.getId());
        List<ItemCarrinhoRespostaDTO> itensResposta = new ArrayList<>();
        List<String> alertas = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        int totalItens = 0;
        boolean podeFinalizar = !itens.isEmpty();

        for (ItemCarrinho item : itens) {
            Produto produto = item.getProduto();
            BigDecimal precoAtual = produto.obterPrecoVigente();

            if (item.getPrecoUnitario() == null || item.getPrecoUnitario().compareTo(precoAtual) != 0) {
                if (item.getPrecoUnitario() != null) {
                    alertas.add("O preço de " + produto.getNome() + " mudou para "
                            + NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(precoAtual) + ".");
                }
                item.setPrecoUnitario(precoAtual);
                itemCarrinhoRepository.save(item);
            }

            int disponivel = disponibilidadeService.calcular(produto);
            boolean temEstoque = item.getQuantidade() <= disponivel;
            if (!temEstoque) {
                podeFinalizar = false;
                alertas.add(disponivel == 0
                        ? produto.getNome() + " esgotou. Remova o item para continuar."
                        : "O estoque de " + produto.getNome() + " mudou: restam " + disponivel
                                + " unidade(s) e você tem " + item.getQuantidade() + " no carrinho.");
            }

            BigDecimal subtotalItem = precoAtual.multiply(BigDecimal.valueOf(item.getQuantidade()));
            subtotal = subtotal.add(subtotalItem);
            totalItens += item.getQuantidade();
            itensResposta.add(new ItemCarrinhoRespostaDTO(
                    produto.getId(),
                    produto.getNome(),
                    produto.getImagemUrl(),
                    item.getQuantidade(),
                    precoAtual,
                    subtotalItem,
                    disponivel == EstoqueDisponibilidadeService.ILIMITADO ? null : disponivel,
                    temEstoque));
        }

        return new CarrinhoRespostaDTO(carrinho.getId(), itensResposta, totalItens, subtotal, podeFinalizar, alertas);
    }

    private void validarSemPedidoPendente(Long usuarioId) {
        usuarioRepository.bloquearParaCheckout(usuarioId);
        if (pedidoRepository.existsByClienteIdAndStatusIn(usuarioId, List.of(StatusPedido.AGUARDANDO_PAGAMENTO))) {
            throw new ConflitoDeEstadoException("Cancele ou conclua o pedido aguardando pagamento antes de alterar o carrinho.");
        }
    }
}
