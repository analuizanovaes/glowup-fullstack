package com.glowup.api.controller;

import com.glowup.api.domain.StatusDevolucao;
import com.glowup.api.dto.AnaliseDevolucaoDTO;
import com.glowup.api.dto.DevolucaoRespostaDTO;
import com.glowup.api.service.DevolucaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/devolucoes")
@RequiredArgsConstructor
@Tag(name = "Admin - Devoluções", description = "Análise sanitária e recebimento de devoluções (perfil ADMIN)")
public class AdminDevolucaoController {

    private final DevolucaoService devolucaoService;

    @GetMapping
    @Operation(summary = "Lista devoluções, com filtro opcional por status")
    public ResponseEntity<List<DevolucaoRespostaDTO>> listarDevolucoes(
            @RequestParam(required = false) StatusDevolucao status) {
        return ResponseEntity.ok(devolucaoService.listarParaAdmin(status));
    }

    @PatchMapping("/{devolucaoId}/analise")
    @Operation(summary = "Analisa a devolução; lacre não condiciona solicitações de arrependimento do art. 49")
    public ResponseEntity<DevolucaoRespostaDTO> analisarDevolucao(
            @PathVariable Long devolucaoId,
            @RequestBody @Valid AnaliseDevolucaoDTO dto) {
        return ResponseEntity.ok(devolucaoService.analisar(devolucaoId, dto));
    }

    @PatchMapping("/{devolucaoId}/recebimento")
    @Operation(summary = "Confirma o recebimento: devolve ao estoque e estorna o pagamento")
    public ResponseEntity<DevolucaoRespostaDTO> confirmarRecebimento(@PathVariable Long devolucaoId) {
        return ResponseEntity.ok(devolucaoService.confirmarRecebimento(devolucaoId));
    }
}
