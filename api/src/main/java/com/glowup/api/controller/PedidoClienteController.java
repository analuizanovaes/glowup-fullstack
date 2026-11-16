package com.glowup.api.controller;

import com.glowup.api.config.Paginacao;
import com.glowup.api.dto.PedidoClienteRespostaDTO;
import com.glowup.api.model.Usuario;
import com.glowup.api.service.PedidoClienteService;
import com.glowup.api.service.PedidoGestaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/pedidos")
@RequiredArgsConstructor
@Tag(name = "Pedidos da cliente", description = "Histórico e acompanhamento dos pedidos da cliente autenticada")
public class PedidoClienteController {

    private final PedidoClienteService pedidoClienteService;
    private final PedidoGestaoService pedidoGestaoService;

    @GetMapping("/meus")
    @Operation(summary = "Lista apenas os pedidos da cliente autenticada",
            description = "Paginação opcional: pagina (começa em 0) e tamanho (padrão 50, máximo 100). Total nos cabeçalhos X-Total-Count e X-Total-Pages.")
    public ResponseEntity<List<PedidoClienteRespostaDTO>> listarMeusPedidos(
            @AuthenticationPrincipal Usuario cliente,
            @RequestParam(required = false) Integer pagina,
            @RequestParam(required = false) Integer tamanho) {
        Page<PedidoClienteRespostaDTO> resultado = pedidoClienteService.listarPedidos(
                cliente.getId(), Paginacao.maisRecentesPrimeiro(pagina, tamanho, "dataCriacao"));
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(resultado.getTotalElements()))
                .header("X-Total-Pages", String.valueOf(resultado.getTotalPages()))
                .body(resultado.getContent());
    }

    @GetMapping("/{pedidoId}")
    @Operation(summary = "Detalha um pedido da cliente autenticada")
    public ResponseEntity<PedidoClienteRespostaDTO> buscarMeuPedido(
            @AuthenticationPrincipal Usuario cliente,
            @PathVariable Long pedidoId) {
        return ResponseEntity.ok(pedidoClienteService.buscarPedido(cliente.getId(), pedidoId));
    }

    @PatchMapping("/{pedidoId}/cancelamento")
    @Operation(summary = "Cancela um pedido próprio que ainda aguarda pagamento")
    public ResponseEntity<Void> cancelarMeuPedido(
            @AuthenticationPrincipal Usuario cliente,
            @PathVariable Long pedidoId) {
        pedidoGestaoService.cancelarPedidoCliente(pedidoId, cliente.getId());
        return ResponseEntity.noContent().build();
    }
}
