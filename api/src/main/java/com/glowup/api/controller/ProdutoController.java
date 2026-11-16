package com.glowup.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.glowup.api.domain.Produto;
import com.glowup.api.config.Paginacao;
import com.glowup.api.dto.ProdutoRequisicaoDTO;
import com.glowup.api.dto.ProdutoRespostaDTO;
import com.glowup.api.repository.ProdutoRepository;
import com.glowup.api.service.ProdutoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@Tag(name = "Catálogo", description = "Consulta pública a clientes logadas; alterações somente da administradora")
@RequestMapping("/api/produtos")
@RequiredArgsConstructor
public class ProdutoController {

    private final ProdutoService produtoService;
    private final ProdutoRepository produtoRepository;

    @Operation(summary = "Lista os produtos ativos",
            description = "Filtros opcionais por categoria e nome (sem diferenciar maiúsculas/acentos), ordem=preco_asc|preco_desc e paginação por pagina (0-based) e tamanho (padrão 50, máximo 100). O total é informado em X-Total-Count e X-Total-Pages.")
    @GetMapping
    public ResponseEntity<List<ProdutoRespostaDTO>> buscarProdutos(
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) String ordem,
            @RequestParam(required = false) Integer pagina,
            @RequestParam(required = false) Integer tamanho) {
        Page<Produto> resultado = produtoService.listarProdutosPaginados(categoria, nome, ordem,
                Paginacao.semOrdenacao(pagina, tamanho));
        List<ProdutoRespostaDTO> produtos = resultado.getContent().stream().map(ProdutoRespostaDTO::de).toList();
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(resultado.getTotalElements()))
                .header("X-Total-Pages", String.valueOf(resultado.getTotalPages()))
                .body(produtos);
    }

    @Operation(summary = "Busca um produto específico pelo ID",
            description = "Retorna os detalhes do produto sem expor o custo de fábrica.")
    @GetMapping("/{id}")
    public ResponseEntity<ProdutoRespostaDTO> buscarPorId(@PathVariable Long id) {
        return produtoRepository.buscarProdutoSimplesPorId(id)
                .map(ProdutoRespostaDTO::de)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Cadastra um produto",
            description = "Somente administradora. Pronta-Entrega exige quantidadeEstoque; Sob Encomenda a ignora.")
    @PostMapping
    public ResponseEntity<Produto> criar(@RequestBody @Valid ProdutoRequisicaoDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(produtoService.criar(dto));
    }

    @Operation(summary = "Edita um produto",
            description = "Somente administradora. A modalidade não pode ser alterada; o saldo é ajustado em /api/admin/estoque.")
    @PutMapping("/{id}")
    public ResponseEntity<Produto> atualizar(@PathVariable Long id, @RequestBody @Valid ProdutoRequisicaoDTO dto) {
        return ResponseEntity.ok(produtoService.atualizar(id, dto));
    }

    @Operation(summary = "Atualiza os preços do produto",
            description = "Somente administradora. Impede preços negativos.")
    @PutMapping("/{id}/precos")
    public ResponseEntity<Produto> atualizarPrecos(
            @PathVariable Long id,
            @RequestBody Map<String, BigDecimal> precos) {
            
        BigDecimal precoBase = precos.get("precoBase");
        BigDecimal precoPromocional = precos.get("precoPromocional");
        
        // Bloqueio de valores negativos apontado na auditoria
        if (precoBase != null && precoBase.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("O preço base não pode ser negativo.");
        }
        if (precoPromocional != null && precoPromocional.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("O preço promocional não pode ser negativo.");
        }
        
        boolean removerPromocao = precos.containsKey("precoPromocional") && precoPromocional == null;
        Produto atualizado = produtoService.atualizarPrecos(id, precoBase, precoPromocional, removerPromocao);
        return ResponseEntity.ok(atualizado);
    }

    @Operation(summary = "Inativa o produto",
            description = "Exclusão lógica (Soft Delete); somente administradora.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desativarProduto(@PathVariable Long id) {
        produtoService.desativarProduto(id);
        return ResponseEntity.noContent().build();
    }
}
