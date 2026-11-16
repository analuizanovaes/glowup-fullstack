package com.glowup.api.controller;

import com.glowup.api.dto.CarrinhoItemRequisicaoDTO;
import com.glowup.api.dto.CarrinhoQuantidadeDTO;
import com.glowup.api.dto.CarrinhoRespostaDTO;
import com.glowup.api.model.Usuario;
import com.glowup.api.service.CarrinhoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Carrinho", description = "Sacola de compras persistida da cliente logada (sempre a própria)")
@RequestMapping("/api/carrinho")
@RequiredArgsConstructor
public class CarrinhoController {

    private final CarrinhoService carrinhoService;

    @Operation(summary = "Consulta o carrinho",
            description = "Recalcula o subtotal em tempo real e alerta sobre mudanças de preço ou de estoque.")
    @GetMapping
    public ResponseEntity<CarrinhoRespostaDTO> consultar(@AuthenticationPrincipal Usuario cliente) {
        return ResponseEntity.ok(carrinhoService.consultar(cliente.getId()));
    }

    @Operation(summary = "Adiciona um produto ao carrinho",
            description = "Soma à quantidade existente. Pronta-Entrega é validada contra o estoque; Sob Encomenda não.")
    @PostMapping("/itens")
    public ResponseEntity<CarrinhoRespostaDTO> adicionarItem(
            @AuthenticationPrincipal Usuario cliente,
            @RequestBody @Valid CarrinhoItemRequisicaoDTO dto) {
        return ResponseEntity.ok(carrinhoService.adicionarItem(cliente.getId(), dto.produtoId(), dto.quantidade()));
    }

    @Operation(summary = "Altera a quantidade de um item")
    @PutMapping("/itens/{produtoId}")
    public ResponseEntity<CarrinhoRespostaDTO> alterarQuantidade(
            @AuthenticationPrincipal Usuario cliente,
            @PathVariable Long produtoId,
            @RequestBody @Valid CarrinhoQuantidadeDTO dto) {
        return ResponseEntity.ok(carrinhoService.alterarQuantidade(cliente.getId(), produtoId, dto.quantidade()));
    }

    @Operation(summary = "Remove um item do carrinho")
    @DeleteMapping("/itens/{produtoId}")
    public ResponseEntity<CarrinhoRespostaDTO> removerItem(
            @AuthenticationPrincipal Usuario cliente,
            @PathVariable Long produtoId) {
        return ResponseEntity.ok(carrinhoService.removerItem(cliente.getId(), produtoId));
    }

    @Operation(summary = "Esvazia o carrinho da cliente logada")
    @DeleteMapping
    public ResponseEntity<CarrinhoRespostaDTO> esvaziar(@AuthenticationPrincipal Usuario cliente) {
        return ResponseEntity.ok(carrinhoService.esvaziar(cliente.getId()));
    }
}
