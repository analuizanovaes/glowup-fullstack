package com.glowup.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.glowup.api.domain.Kit;
import com.glowup.api.dto.KitRequisicaoDTO;
import com.glowup.api.dto.KitRespostaDTO;
import com.glowup.api.config.Paginacao;
import com.glowup.api.service.KitService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Tag(name = "Catálogo - Kits", description = "Kits de fábrica e kits manuais")
@RequestMapping("/api/kits")
@RequiredArgsConstructor
public class KitController {

    private final KitService kitService;

    @Operation(summary = "Lista os kits",
            description = "Filtro opcional por categoria e ordenação por ordem=preco_asc|preco_desc. Paginação por pagina (começa em 0) e tamanho (padrão 50, máximo 100). O total é informado em X-Total-Count e X-Total-Pages.")
    @GetMapping
    public ResponseEntity<List<KitRespostaDTO>> listar(
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) String ordem,
            @RequestParam(required = false) Integer pagina,
            @RequestParam(required = false) Integer tamanho) {
        Page<Kit> resultado = kitService.listarTodosPaginados(categoria, ordem,
                Paginacao.semOrdenacao(pagina, tamanho));
        List<KitRespostaDTO> kits = resultado.getContent().stream()
                .map(KitRespostaDTO::de)
                .toList();
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(resultado.getTotalElements()))
                .header("X-Total-Pages", String.valueOf(resultado.getTotalPages()))
                .body(kits);
    }

    @Operation(summary = "Busca um kit pelo ID")
    @GetMapping("/{id}")
    public ResponseEntity<KitRespostaDTO> buscarPorId(@PathVariable Long id) {
        return kitService.buscarPorId(id)
                .map(KitRespostaDTO::de)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Cria um kit",
            description = "Somente administradora.")
    @PostMapping
    public ResponseEntity<KitRespostaDTO> criar(@RequestBody @Valid KitRequisicaoDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(KitRespostaDTO.de(kitService.criar(dto)));
    }

    @Operation(summary = "Edita um kit",
            description = "Somente administradora. Tipo e modalidade não podem ser alterados; kits manuais têm os componentes substituídos pela lista enviada.")
    @PutMapping("/{id}")
    public ResponseEntity<KitRespostaDTO> atualizar(@PathVariable Long id, @RequestBody @Valid KitRequisicaoDTO dto) {
        return ResponseEntity.ok(KitRespostaDTO.de(kitService.atualizar(id, dto)));
    }

    @Operation(summary = "Inativa um kit",
            description = "Somente administradora.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        if (kitService.buscarPorId(id).isPresent()) {
            kitService.deletar(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
