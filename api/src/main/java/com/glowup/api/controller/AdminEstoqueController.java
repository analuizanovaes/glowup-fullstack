package com.glowup.api.controller;

import com.glowup.api.dto.EstoqueAjusteDTO;
import com.glowup.api.dto.EstoqueEntradaDTO;
import com.glowup.api.dto.EstoqueRespostaDTO;
import com.glowup.api.service.EstoqueAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Tag(name = "Admin - Estoque", description = "Controle físico da Pronta-Entrega (somente administradora)")
@RequestMapping("/api/admin/estoque")
@RequiredArgsConstructor
public class AdminEstoqueController {

    private final EstoqueAdminService estoqueAdminService;

    @Operation(summary = "Lista o saldo físico dos produtos de Pronta-Entrega")
    @GetMapping
    public ResponseEntity<List<EstoqueRespostaDTO>> listar() {
        return ResponseEntity.ok(estoqueAdminService.listar());
    }

    @Operation(summary = "Registra uma entrada de estoque (soma ao saldo)")
    @PostMapping("/{produtoId}/entrada")
    public ResponseEntity<EstoqueRespostaDTO> registrarEntrada(
            @PathVariable Long produtoId, @RequestBody @Valid EstoqueEntradaDTO dto) {
        return ResponseEntity.ok(estoqueAdminService.registrarEntrada(produtoId, dto));
    }

    @Operation(summary = "Ajusta o saldo (contagem de inventário)")
    @PutMapping("/{produtoId}")
    public ResponseEntity<EstoqueRespostaDTO> ajustar(
            @PathVariable Long produtoId, @RequestBody @Valid EstoqueAjusteDTO dto) {
        return ResponseEntity.ok(estoqueAdminService.ajustarSaldo(produtoId, dto));
    }
}
