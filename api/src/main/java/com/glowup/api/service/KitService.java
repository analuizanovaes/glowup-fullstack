package com.glowup.api.service;

import com.glowup.api.domain.Categoria;
import com.glowup.api.domain.ItemKit;
import com.glowup.api.domain.Kit;
import com.glowup.api.domain.ModalidadeProduto;
import com.glowup.api.domain.Produto;
import com.glowup.api.domain.TipoKit;
import com.glowup.api.domain.TipoMovimentacao;
import com.glowup.api.dto.ItemKitRequisicaoDTO;
import com.glowup.api.dto.KitRequisicaoDTO;
import com.glowup.api.exception.RecursoNaoEncontradoException;
import com.glowup.api.exception.ConflitoDeEstadoException;
import com.glowup.api.repository.CategoriaRepository;
import com.glowup.api.repository.KitRepository;
import com.glowup.api.repository.ProdutoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.text.Normalizer;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class KitService {

    private final KitRepository kitRepository;
    private final ProdutoRepository produtoRepository;
    private final CategoriaRepository categoriaRepository;
    private final MovimentacaoEstoqueService movimentacaoEstoqueService;

    @Transactional(readOnly = true)
    public Page<Kit> listarTodosPaginados(String categoria, String ordem, Pageable pageable) {
        Sort ordenacao = Sort.by(Sort.Order.asc("id"));
        if ("preco_asc".equalsIgnoreCase(ordem)) {
            ordenacao = JpaSort.unsafe(Sort.Direction.ASC, expressaoPrecoVigente()).and(ordenacao);
        } else if ("preco_desc".equalsIgnoreCase(ordem)) {
            ordenacao = JpaSort.unsafe(Sort.Direction.DESC, expressaoPrecoVigente()).and(ordenacao);
        } else if (ordem != null && !ordem.isBlank()) {
            throw new IllegalArgumentException("Ordenação inválida. Use preco_asc ou preco_desc.");
        }
        Pageable paginacaoOrdenada = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), ordenacao);
        String categoriaBusca = normalizar(categoria);
        return kitRepository.buscarComFiltrosPaginados(
                categoriaBusca.isEmpty() ? null : categoriaBusca, paginacaoOrdenada);
    }

    private String expressaoPrecoVigente() {
        return "case when (k.precoPromocional is not null and (k.dataInicioOferta is null or k.dataInicioOferta <= CURRENT_TIMESTAMP) "
                + "and (k.dataLimitePromocao is null or k.dataLimitePromocao >= CURRENT_TIMESTAMP)) "
                + "then k.precoPromocional else k.precoBase end";
    }

    private String normalizar(String texto) {
        if (texto == null) return "";
        String semAcento = Normalizer.normalize(texto.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcento.toLowerCase(Locale.ROOT);
    }

    @Transactional(readOnly = true)
    public Optional<Kit> buscarPorId(Long id) {
        return kitRepository.findById(id);
    }

    /** Cria o kit a partir do DTO validado: a administradora nunca preenche campos internos da entidade. */
    @Transactional
    public Kit criar(KitRequisicaoDTO dto) {
        validarRegras(dto);

        Categoria categoria = categoriaRepository.findByNomeIgnoreCase(dto.categoria().trim())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Categoria não encontrada. Cadastre-a em /api/admin/categorias."));

        Kit kit = new Kit();
        kit.setNome(dto.nome().trim());
        kit.setDescricao(dto.descricao());
        kit.setMarca(dto.marca().trim());
        kit.setCategoria(categoria);
        kit.setCustoFabrica(dto.custoFabrica());
        kit.setPrecoBase(dto.precoBase());
        kit.setPrecoPromocional(dto.precoPromocional());
        kit.setDataInicioOferta(dto.dataInicioOferta());
        kit.setDataLimitePromocao(dto.dataLimitePromocao());
        kit.setModalidade(dto.modalidade());
        kit.setTipo(dto.tipo());

        if (dto.modalidade() == ModalidadeProduto.PRONTA_ENTREGA) {
            // Kit MANUAL não tem saldo próprio (baixa os componentes); 0 só satisfaz a exigência de estoque.
            kit.setQuantidadeEstoque(dto.tipo() == TipoKit.FABRICA ? dto.quantidadeEstoque() : 0);
        }

        if (dto.tipo() == TipoKit.MANUAL) {
            for (ItemKitRequisicaoDTO itemDto : dto.itens()) {
                Produto componente = buscarComponente(itemDto.produtoId());
                ItemKit item = new ItemKit();
                item.setKit(kit);
                item.setProduto(componente);
                item.setQuantidade(itemDto.quantidade());
                kit.getItens().add(item);
            }
        }

        Kit salvo = kitRepository.save(kit);
        if (dto.tipo() == TipoKit.FABRICA && salvo.getQuantidadeEstoque() != null && salvo.getQuantidadeEstoque() > 0) {
            movimentacaoEstoqueService.registrar(salvo, TipoMovimentacao.ENTRADA, salvo.getQuantidadeEstoque(), null,
                    "Estoque inicial do cadastro do kit");
        }
        return salvo;
    }

    /** Tipo e modalidade não mudam na edição; o saldo de um kit de fábrica é ajustado em /api/admin/estoque. */
    @Transactional
    public Kit atualizar(Long id, KitRequisicaoDTO dto) {
        Kit kit = kitRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Kit não encontrado."));
        if (dto.tipo() != kit.getTipo()) {
            throw new IllegalArgumentException("O tipo do kit não pode ser alterado. Cadastre um novo kit.");
        }
        if (dto.modalidade() != kit.getModalidade()) {
            throw new IllegalArgumentException("A modalidade do kit não pode ser alterada. Cadastre um novo kit.");
        }
        validarRegras(dto);

        Categoria categoria = categoriaRepository.findByNomeIgnoreCase(dto.categoria().trim())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Categoria não encontrada. Cadastre-a em /api/admin/categorias."));

        kit.setNome(dto.nome().trim());
        kit.setDescricao(dto.descricao());
        kit.setMarca(dto.marca().trim());
        kit.setCategoria(categoria);
        kit.setCustoFabrica(dto.custoFabrica());
        kit.setPrecoBase(dto.precoBase());
        kit.setPrecoPromocional(dto.precoPromocional());
        kit.setDataInicioOferta(dto.dataInicioOferta());
        kit.setDataLimitePromocao(dto.dataLimitePromocao());

        if (kit.getTipo() == TipoKit.MANUAL) {
            atualizarComponentes(kit, dto.itens());
        }
        return kitRepository.save(kit);
    }

    /** Atualiza os componentes no lugar: recriar a lista violaria a unicidade (kit, componente) no mesmo flush. */
    private void atualizarComponentes(Kit kit, List<ItemKitRequisicaoDTO> novos) {
        Map<Long, Integer> quantidades = new LinkedHashMap<>();
        for (ItemKitRequisicaoDTO itemDto : novos) {
            if (itemDto.produtoId().equals(kit.getId())) {
                throw new IllegalArgumentException("Um kit não pode ser componente de si mesmo.");
            }
            quantidades.put(itemDto.produtoId(), itemDto.quantidade());
        }

        kit.getItens().removeIf(item -> !quantidades.containsKey(item.getProdutoIdPersistido()));

        for (ItemKit existente : kit.getItens()) {
            Long produtoId = existente.getProdutoIdPersistido();
            if (existente.getProduto() == null) {
                throw new ConflitoDeEstadoException("O kit contém um componente inativo. Remova-o da lista de componentes antes de atualizar o kit.");
            }
            validarComponentePermitido(existente.getProduto());
            existente.setQuantidade(quantidades.remove(produtoId));
        }

        for (Map.Entry<Long, Integer> novo : quantidades.entrySet()) {
            Produto componente = buscarComponente(novo.getKey());
            ItemKit item = new ItemKit();
            item.setKit(kit);
            item.setProduto(componente);
            item.setQuantidade(novo.getValue());
            kit.getItens().add(item);
        }
    }

    @Transactional
    public void deletar(Long id) {
        Kit kit = kitRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Kit não encontrado."));
        kit.setAtivo(false);
        kitRepository.save(kit);
    }

    private Produto buscarComponente(Long produtoId) {
        Produto componente = produtoRepository.findById(produtoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Produto componente " + produtoId + " não encontrado."));
        validarComponentePermitido(componente);
        return componente;
    }

    private void validarComponentePermitido(Produto componente) {
        if (componente instanceof Kit kit && kit.getTipo() == TipoKit.MANUAL) {
            throw new IllegalArgumentException("Um kit manual não pode ser componente de outro kit manual.");
        }
    }

    private void validarRegras(KitRequisicaoDTO dto) {
        if (dto.precoPromocional() != null && dto.precoPromocional().compareTo(dto.precoBase()) > 0) {
            throw new IllegalArgumentException("O preço promocional não pode ser maior que o preço base.");
        }
        if (dto.precoPromocional() != null && dto.dataLimitePromocao() == null) {
            throw new IllegalArgumentException("Informe a data de fim da promoção.");
        }
        if (dto.precoPromocional() == null
                && (dto.dataLimitePromocao() != null || dto.dataInicioOferta() != null)) {
            throw new IllegalArgumentException("Datas de promoção exigem um preço promocional.");
        }
        if (dto.dataInicioOferta() != null && dto.dataLimitePromocao() != null
                && !dto.dataLimitePromocao().isAfter(dto.dataInicioOferta())) {
            throw new IllegalArgumentException("O fim da promoção deve ser posterior ao início.");
        }
        if (dto.tipo() == TipoKit.FABRICA) {
            if (dto.modalidade() == ModalidadeProduto.PRONTA_ENTREGA && dto.quantidadeEstoque() == null) {
                throw new IllegalArgumentException("A quantidade em estoque é obrigatória para kits de fábrica em Pronta-Entrega.");
            }
            return;
        }
        if (dto.itens() == null || dto.itens().isEmpty()) {
            throw new IllegalArgumentException("Kit manual exige ao menos um produto componente.");
        }
        Set<Long> vistos = new HashSet<>();
        for (ItemKitRequisicaoDTO item : dto.itens()) {
            if (!vistos.add(item.produtoId())) {
                throw new IllegalArgumentException("O produto " + item.produtoId() + " está repetido nos componentes.");
            }
        }
    }
}
