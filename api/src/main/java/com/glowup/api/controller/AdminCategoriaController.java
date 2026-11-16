package com.glowup.api.controller;

import com.glowup.api.domain.Categoria;
import com.glowup.api.dto.CategoriaRequisicaoDTO;
import com.glowup.api.service.CategoriaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Tag(name = "Admin - Categorias", description = "CRUD de categorias (somente administradora)")
@RequestMapping("/api/admin/categorias")
@RequiredArgsConstructor
public class AdminCategoriaController {

    private final CategoriaService categoriaService;

    @Operation(summary = "Lista as categorias")
    @GetMapping
    public ResponseEntity<List<Categoria>> listar() {
        return ResponseEntity.ok(categoriaService.listar());
    }

    @Operation(summary = "Cria uma categoria")
    @PostMapping
    public ResponseEntity<Categoria> criar(@RequestBody @Valid CategoriaRequisicaoDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoriaService.criar(dto));
    }

    @Operation(summary = "Renomeia uma categoria")
    @PutMapping("/{id}")
    public ResponseEntity<Categoria> renomear(@PathVariable Long id, @RequestBody @Valid CategoriaRequisicaoDTO dto) {
        return ResponseEntity.ok(categoriaService.renomear(id, dto));
    }

    @Operation(summary = "Exclui (logicamente) uma categoria sem produtos ativos")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        categoriaService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
