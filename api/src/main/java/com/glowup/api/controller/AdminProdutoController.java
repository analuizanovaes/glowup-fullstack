package com.glowup.api.controller;

import com.glowup.api.dto.ProdutoAdminRespostaDTO;
import com.glowup.api.config.Paginacao;
import com.glowup.api.repository.ProdutoRepository;
import com.glowup.api.service.ProdutoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Tag(name = "Admin - Catálogo", description = "Listagem completa do catálogo (somente administradora)")
@RequestMapping("/api/admin/produtos")
@RequiredArgsConstructor
public class AdminProdutoController {

    private final ProdutoRepository produtoRepository;
    private final ProdutoService produtoService;

    @Operation(summary = "Lista todos os produtos e kits, inclusive os inativos",
            description = "Cada linha traz o campo ativo e, nos kits, o tipoKit. Paginação por pagina (0-based) e tamanho (padrão 50, máximo 100); total em X-Total-Count e X-Total-Pages.")
    @GetMapping
    public ResponseEntity<List<ProdutoAdminRespostaDTO>> listar(
            @RequestParam(required = false) Integer pagina,
            @RequestParam(required = false) Integer tamanho) {
        var solicitada = Paginacao.maisRecentesPrimeiro(pagina, tamanho, "id");
        Page<ProdutoAdminRespostaDTO> resultado = produtoRepository.listarTodosIncluindoInativos(
                        PageRequest.of(solicitada.getPageNumber(), solicitada.getPageSize(), Sort.by(Sort.Order.asc("id"))))
                .map(ProdutoAdminRespostaDTO::de);
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(resultado.getTotalElements()))
                .header("X-Total-Pages", String.valueOf(resultado.getTotalPages()))
                .body(resultado.getContent());
    }

    @Operation(summary = "Reativa um produto ou kit inativado",
            description = "Desfaz a exclusão lógica: o item volta à vitrine com o mesmo preço, estoque e histórico, sem novo cadastro.")
    @PatchMapping("/{id}/reativar")
    public ResponseEntity<Void> reativar(@PathVariable Long id) {
        produtoService.reativarProduto(id);
        return ResponseEntity.noContent().build();
    }
}
