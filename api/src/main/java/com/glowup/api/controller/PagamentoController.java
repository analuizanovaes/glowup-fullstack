package com.glowup.api.controller;

import com.glowup.api.dto.PagamentoRequisicaoDTO;
import com.glowup.api.dto.PagamentoRespostaDTO;
import com.glowup.api.model.Usuario;
import com.glowup.api.service.PagamentoService;
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

@RestController
@RequestMapping("/api/pagamentos")
@RequiredArgsConstructor
@Tag(name = "Pagamentos", description = "Cobrança de pedidos via Mercado Pago (Pix e cartão em até 3x)")
public class PagamentoController {

    private final PagamentoService pagamentoService;

    @PostMapping
    @Operation(summary = "Inicia o pagamento de um pedido da cliente autenticada")
    public ResponseEntity<PagamentoRespostaDTO> iniciarPagamento(
            @AuthenticationPrincipal Usuario cliente,
            @RequestBody @Valid PagamentoRequisicaoDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pagamentoService.iniciarPagamento(cliente, dto));
    }

    @GetMapping("/pedido/{pedidoId}")
    @Operation(summary = "Consulta o pagamento de um pedido da cliente autenticada")
    public ResponseEntity<PagamentoRespostaDTO> consultarPagamento(
            @AuthenticationPrincipal Usuario cliente,
            @PathVariable Long pedidoId) {
        return ResponseEntity.ok(pagamentoService.consultarPorPedido(cliente, pedidoId));
    }

    @PostMapping("/pedido/{pedidoId}/sincronizar")
    @Operation(summary = "Reconsulta o status no Mercado Pago e atualiza o pedido (útil se o webhook não chegou)")
    public ResponseEntity<PagamentoRespostaDTO> sincronizarPagamento(
            @AuthenticationPrincipal Usuario cliente,
            @PathVariable Long pedidoId) {
        return ResponseEntity.ok(pagamentoService.sincronizarComGateway(cliente, pedidoId));
    }
}
