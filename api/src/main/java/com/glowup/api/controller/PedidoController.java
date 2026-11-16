package com.glowup.api.controller;

import com.glowup.api.domain.Pedido;
import com.glowup.api.dto.CheckoutRequisicaoDTO;
import com.glowup.api.dto.CheckoutRespostaDTO;
import com.glowup.api.dto.CheckoutSimulacaoDTO;
import com.glowup.api.dto.CheckoutSimulacaoRespostaDTO;
import com.glowup.api.model.Usuario;
import com.glowup.api.service.PedidoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Pedidos - Checkout", description = "Criação do pedido a partir do carrinho da cliente logada")
@RequestMapping("/api/pedidos")
@RequiredArgsConstructor
public class PedidoController {

    private final PedidoService pedidoService;

    @PostMapping("/simulacao")
    @io.swagger.v3.oas.annotations.Operation(summary = "Simula os valores do checkout sem criar pedido ou consumir cupom")
    public ResponseEntity<CheckoutSimulacaoRespostaDTO> simularCheckout(
            @AuthenticationPrincipal Usuario cliente, @RequestBody CheckoutSimulacaoDTO dto) {
        return ResponseEntity.ok(pedidoService.simularCheckout(cliente, dto));
    }

    @Operation(summary = "Finaliza o checkout e cria o pedido aguardando pagamento",
            description = "Usa os itens do carrinho salvo no servidor. A cliente vem do token e todos os valores "
                    + "(subtotal, cupom, frete, total, prazo) são calculados pelo servidor. Em seguida use POST /api/pagamentos.")
    @PostMapping("/checkout")
    public ResponseEntity<CheckoutRespostaDTO> realizarCheckout(
            @AuthenticationPrincipal Usuario cliente,
            @RequestBody @Valid CheckoutRequisicaoDTO dto,
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey) { 
        
        if (idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
            throw new IllegalArgumentException("Idempotency-Key inválida: informe de 1 a 100 caracteres.");
        }
        Pedido pedidoSalvo = pedidoService.finalizarCheckout(cliente, dto, idempotencyKey); 
        
        return ResponseEntity.status(HttpStatus.CREATED).body(CheckoutRespostaDTO.de(pedidoSalvo));
    }
}
