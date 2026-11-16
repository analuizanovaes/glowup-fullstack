package com.glowup.api.controller;

import com.glowup.api.dto.DevolucaoRespostaDTO;
import com.glowup.api.dto.SolicitacaoDevolucaoDTO;
import com.glowup.api.model.Usuario;
import com.glowup.api.service.DevolucaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Devoluções (cliente)", description = "Solicitação de devolução em até 7 dias após a entrega")
public class DevolucaoController {

    private final DevolucaoService devolucaoService;

    @PostMapping("/pedidos/{pedidoId}/devolucao")
    @Operation(summary = "Solicita a devolução de um pedido entregue")
    public ResponseEntity<DevolucaoRespostaDTO> solicitarDevolucao(
            @AuthenticationPrincipal Usuario cliente,
            @PathVariable Long pedidoId,
            @RequestBody @Valid SolicitacaoDevolucaoDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(devolucaoService.solicitar(cliente, pedidoId, dto));
    }

    @GetMapping("/devolucoes/minhas")
    @Operation(summary = "Lista as devoluções da cliente autenticada")
    public ResponseEntity<List<DevolucaoRespostaDTO>> listarMinhasDevolucoes(@AuthenticationPrincipal Usuario cliente) {
        return ResponseEntity.ok(devolucaoService.listarDaCliente(cliente));
    }
}
