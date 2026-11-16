package com.glowup.api.service;

import com.glowup.api.domain.Categoria;
import com.glowup.api.domain.ModalidadeProduto;
import com.glowup.api.domain.Produto;
import com.glowup.api.domain.TipoMovimentacao;
import com.glowup.api.dto.ProdutoRequisicaoDTO;
import com.glowup.api.exception.ConflitoDeEstadoException;
import com.glowup.api.exception.RecursoNaoEncontradoException;
import com.glowup.api.repository.CategoriaRepository;
import com.glowup.api.repository.ProdutoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.Locale;

/** Catálogo (RF02 / RF07): busca com filtros e ordenação por preço e CRUD da administradora com Soft Delete. */
@Service
@RequiredArgsConstructor
public class ProdutoService {

    private final ProdutoRepository produtoRepository;
    private final CategoriaRepository categoriaRepository;
    private final MovimentacaoEstoqueService movimentacaoEstoqueService;
    private final EstoqueAdminService estoqueAdminService;

    /**
     * Lista produtos ativos (o Soft Delete já oculta os inativos). Filtros de categoria e nome ignoram
     * maiúsculas e acentos; ordem aceita "preco_asc" / "preco_desc" e usa o preço vigente (promoção inclusa).
     */
    @Transactional(readOnly = true)
    public Page<Produto> listarProdutosPaginados(String categoria, String nome, String ordem, Pageable pageable) {
        String categoriaBusca = normalizar(categoria);
        String nomeBusca = normalizar(nome);
        String nomeLike = escaparLike(nomeBusca);

        Sort ordenacao = Sort.by(Sort.Order.asc("id"));
        if ("preco_asc".equalsIgnoreCase(ordem)) {
            ordenacao = JpaSort.unsafe(Sort.Direction.ASC, expressaoPrecoVigente()).and(ordenacao);
        } else if ("preco_desc".equalsIgnoreCase(ordem)) {
            ordenacao = JpaSort.unsafe(Sort.Direction.DESC, expressaoPrecoVigente()).and(ordenacao);
        } else if (ordem != null && !ordem.isBlank()) {
            throw new IllegalArgumentException("Ordenação inválida. Use preco_asc ou preco_desc.");
        }

        Pageable paginacaoOrdenada = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), ordenacao);
        return produtoRepository.buscarComFiltrosPaginados(
                categoriaBusca.isEmpty() ? null : categoriaBusca,
                nomeLike.isEmpty() ? null : nomeLike,
                paginacaoOrdenada);
    }

    private String expressaoPrecoVigente() {
        return "case when (p.precoPromocional is not null and (p.dataInicioOferta is null or p.dataInicioOferta <= CURRENT_TIMESTAMP) "
                + "and (p.dataLimitePromocao is null or p.dataLimitePromocao >= CURRENT_TIMESTAMP)) "
                + "then p.precoPromocional else p.precoBase end";
    }

    @Transactional
    public Produto criar(ProdutoRequisicaoDTO dto) {
        validarRegras(dto);
        Produto produto = new Produto();
        aplicarDados(produto, dto);
        produto.setModalidade(dto.modalidade());
        if (dto.modalidade() == ModalidadeProduto.PRONTA_ENTREGA) {
            produto.setQuantidadeEstoque(dto.quantidadeEstoque());
            produto.setDataValidade(dto.dataValidade());
        }
        Produto salvo = produtoRepository.save(produto);
        movimentacaoEstoqueService.registrar(salvo, TipoMovimentacao.ENTRADA,
                salvo.getQuantidadeEstoque() == null ? 0 : salvo.getQuantidadeEstoque(), null,
                "Estoque inicial do cadastro do produto");
        return salvo;
    }

    /**
     * A modalidade só pode mudar de Sob Encomenda para Pronta-Entrega (a pessoa passa a ter o produto em mãos e
     * informa o estoque inicial, que entra no histórico). O caminho inverso continua bloqueado: descartar um
     * saldo físico apagaria a rastreabilidade do estoque.
     */
    @Transactional
    public Produto atualizar(Long id, ProdutoRequisicaoDTO dto) {
        Produto produto = buscarOuFalhar(id);
        boolean passandoParaProntaEntrega = produto.getModalidade() == ModalidadeProduto.SOB_ENCOMENDA
                && dto.modalidade() == ModalidadeProduto.PRONTA_ENTREGA;
        if (dto.modalidade() != produto.getModalidade() && !passandoParaProntaEntrega) {
            throw new IllegalArgumentException(
                    "Um produto de Pronta-Entrega não pode voltar para Sob Encomenda. Cadastre um novo produto.");
        }
        if (passandoParaProntaEntrega) {
            validarRegras(dto);
            aplicarDados(produto, dto);
            produto.setModalidade(ModalidadeProduto.PRONTA_ENTREGA);
            produto.setQuantidadeEstoque(dto.quantidadeEstoque());
            produto.setDataValidade(dto.dataValidade());
            Produto salvo = produtoRepository.save(produto);
            movimentacaoEstoqueService.registrar(salvo, TipoMovimentacao.ENTRADA,
                    salvo.getQuantidadeEstoque() == null ? 0 : salvo.getQuantidadeEstoque(), null,
                    "Estoque inicial ao mudar para Pronta-Entrega");
            return salvo;
        }
        validarRegras(dto);
        aplicarDados(produto, dto);
        if (produto.getEstoque() != null) {
            if (!java.util.Objects.equals(produto.getQuantidadeEstoque(), dto.quantidadeEstoque())) {
                estoqueAdminService.ajustarSaldo(id, new com.glowup.api.dto.EstoqueAjusteDTO(
                        dto.quantidadeEstoque(), null, dto.dataValidade(), "Ajuste pela edição do produto"));
            }
            produto.setDataValidade(dto.dataValidade());
        }
        return produtoRepository.save(produto);
    }

    @Transactional
    public void desativarProduto(Long id) {
        Produto produto = buscarOuFalhar(id);
        produto.setAtivo(false);
        produtoRepository.save(produto);
    }

    /** Desfaz a exclusão lógica: o produto (ou kit) volta à vitrine com o mesmo preço, estoque e histórico. */
    @Transactional
    public void reativarProduto(Long id) {
        if (produtoRepository.contarInativosPorId(id) == 0) {
            throw new RecursoNaoEncontradoException("Produto inativo não encontrado.");
        }
        if (produtoRepository.reativarPorId(id) == 0) {
            throw new ConflitoDeEstadoException(
                    "A categoria deste produto está inativa. Reative a categoria antes de reativar o produto.");
        }
    }

    @Transactional
    public Produto atualizarPrecos(Long id, BigDecimal novoPrecoBase, BigDecimal novoPrecoPromocional) {
        return atualizarPrecos(id, novoPrecoBase, novoPrecoPromocional, false);
    }

    /** removerPromocao = true quando o corpo trouxe "precoPromocional": null de forma explícita. */
    @Transactional
    public Produto atualizarPrecos(Long id, BigDecimal novoPrecoBase, BigDecimal novoPrecoPromocional,
                                   boolean removerPromocao) {
        Produto produto = buscarOuFalhar(id);
        if (removerPromocao) {
            produto.setPrecoPromocional(null);
            produto.setDataInicioOferta(null);
            produto.setDataLimitePromocao(null);
        }

        if (novoPrecoBase != null) {
            produto.setPrecoBase(novoPrecoBase);
        }

        if (novoPrecoPromocional != null) {
            produto.setPrecoPromocional(novoPrecoPromocional);
        } else if (produto.getPrecoPromocional() == null) {
            produto.setDataInicioOferta(null);
            produto.setDataLimitePromocao(null);
        } else if (produto.getDataLimitePromocao() == null) {
            throw new IllegalArgumentException(
                    "Para definir preço promocional informe a data de fim pela edição do produto (PUT /api/produtos/{id}).");
        }
        validarPrecos(produto.getPrecoBase(), produto.getPrecoPromocional());
        return produtoRepository.save(produto);
    }

    // ------------------------------------------------------------------ regras

    private Produto buscarOuFalhar(Long id) {
        return produtoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto não encontrado."));
    }

    private void validarRegras(ProdutoRequisicaoDTO dto) {
        if (dto.modalidade() == ModalidadeProduto.PRONTA_ENTREGA && dto.quantidadeEstoque() == null) {
            throw new IllegalArgumentException("A quantidade em estoque é obrigatória para produtos de Pronta-Entrega.");
        }
        validarPrecos(dto.precoBase(), dto.precoPromocional());
        if (dto.precoPromocional() != null && dto.dataLimitePromocao() == null) {
            throw new IllegalArgumentException("Informe a data de fim da promoção.");
        }
        if (dto.precoPromocional() == null
                && (dto.dataLimitePromocao() != null || dto.dataInicioOferta() != null)) {
            throw new IllegalArgumentException("Datas de promoção exigem um preço promocional.");
        }
        LocalDateTime inicio = dto.dataInicioOferta();
        LocalDateTime fim = dto.dataLimitePromocao();
        if (inicio != null && fim != null && !fim.isAfter(inicio)) {
            throw new IllegalArgumentException("O fim da promoção deve ser posterior ao início.");
        }
    }

    private void validarPrecos(BigDecimal precoBase, BigDecimal precoPromocional) {
        if (precoPromocional != null && precoBase != null && precoPromocional.compareTo(precoBase) > 0) {
            throw new IllegalArgumentException("O preço promocional não pode ser maior que o preço base.");
        }
    }

    private void aplicarDados(Produto produto, ProdutoRequisicaoDTO dto) {
        Categoria categoria = categoriaRepository.findByNomeIgnoreCase(dto.categoria().trim())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Categoria não encontrada. Cadastre-a em /api/admin/categorias."));
        produto.setNome(dto.nome().trim());
        produto.setDescricao(dto.descricao());
        produto.setMarca(dto.marca().trim());
        produto.setCategoria(categoria);
        produto.setCustoFabrica(dto.custoFabrica());
        produto.setPrecoBase(dto.precoBase());
        produto.setPrecoPromocional(dto.precoPromocional());
        produto.setDataInicioOferta(dto.dataInicioOferta());
        produto.setDataLimitePromocao(dto.dataLimitePromocao());
        produto.setTempoMedioUsoDias(dto.tempoMedioUsoDias());
        produto.setImagemUrl(dto.imagemUrl() == null || dto.imagemUrl().isBlank() ? null : dto.imagemUrl().trim());
    }

    private String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String semAcento = Normalizer.normalize(texto.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcento.toLowerCase(Locale.ROOT);
    }

    private String escaparLike(String texto) {
        return texto.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
