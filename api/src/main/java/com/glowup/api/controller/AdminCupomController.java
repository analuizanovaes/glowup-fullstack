package com.glowup.api.controller;

import com.glowup.api.domain.Cupom;
import com.glowup.api.dto.CupomRequisicaoDTO;
import com.glowup.api.service.CupomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Tag(name = "Admin - Cupons", description = "CRUD de cupons de desconto (somente administradora)")
@RequestMapping("/api/admin/cupons")
@RequiredArgsConstructor
public class AdminCupomController {

    private final CupomService cupomService;

    @Operation(summary = "Lista os cupons")
    @GetMapping
    public ResponseEntity<List<Cupom>> listar() {
        return ResponseEntity.ok(cupomService.listar());
    }

    @Operation(summary = "Cria um cupom", description = "Informe percentualDesconto OU valorDesconto.")
    @PostMapping
    public ResponseEntity<Cupom> criar(@RequestBody @Valid CupomRequisicaoDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cupomService.criar(dto));
    }

    @Operation(summary = "Edita um cupom")
    @PutMapping("/{id}")
    public ResponseEntity<Cupom> atualizar(@PathVariable Long id, @RequestBody @Valid CupomRequisicaoDTO dto) {
        return ResponseEntity.ok(cupomService.atualizar(id, dto));
    }

    @Operation(summary = "Desativa um cupom", description = "Exclusão lógica: pedidos antigos mantêm o vínculo.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desativar(@PathVariable Long id) {
        cupomService.desativar(id);
        return ResponseEntity.noContent().build();
    }
}
