package com.glowup.api.controller;

import com.glowup.api.config.Paginacao;
import com.glowup.api.domain.StatusPedido;
import com.glowup.api.dto.AtualizacaoStatusPedidoDTO;
import com.glowup.api.dto.PedidoAdminRespostaDTO;
import com.glowup.api.model.Usuario;
import com.glowup.api.service.PedidoGestaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/pedidos")
@RequiredArgsConstructor
@Tag(name = "Admin - Pedidos", description = "Gestão de pedidos e Lucro Real (perfil ADMIN)")
public class AdminPedidoController {

    private final PedidoGestaoService pedidoGestaoService;

    @GetMapping
    @Operation(summary = "Lista os pedidos, com filtro opcional por status",
            description = "Paginação opcional: pagina (começa em 0) e tamanho (padrão 50, máximo 100). Total nos cabeçalhos X-Total-Count e X-Total-Pages.")
    public ResponseEntity<List<PedidoAdminRespostaDTO>> listarPedidos(
            @RequestParam(required = false) StatusPedido status,
            @RequestParam(required = false) Integer pagina,
            @RequestParam(required = false) Integer tamanho) {
        Page<PedidoAdminRespostaDTO> resultado = pedidoGestaoService.listar(
                status, Paginacao.maisRecentesPrimeiro(pagina, tamanho, "dataCriacao"));
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(resultado.getTotalElements()))
                .header("X-Total-Pages", String.valueOf(resultado.getTotalPages()))
                .body(resultado.getContent());
    }

    @GetMapping("/{pedidoId}")
    @Operation(summary = "Detalha um pedido com custo de fábrica e Lucro Real")
    public ResponseEntity<PedidoAdminRespostaDTO> buscarPedido(@PathVariable Long pedidoId) {
        return ResponseEntity.ok(pedidoGestaoService.buscar(pedidoId));
    }

    @PatchMapping("/{pedidoId}/endereco-conferido")
    @Operation(summary = "Marca o endereço de entrega como conferido",
            description = "Pedidos criados com o ViaCEP fora do ar ficam com conferirEndereco=true e não podem ir para "
                    + "Em Separação/Enviado até a administradora conferir bairro e CEP.")
    public ResponseEntity<PedidoAdminRespostaDTO> enderecoConferido(
            @AuthenticationPrincipal Usuario administradora, @PathVariable Long pedidoId) {
        return ResponseEntity.ok(pedidoGestaoService.marcarEnderecoConferido(pedidoId, administradora.getEmail()));
    }

    @PatchMapping("/{pedidoId}/status")
    @Operation(summary = "Atualiza o status logístico (Pago > Em Separação > Enviado > Entregue)")
    public ResponseEntity<PedidoAdminRespostaDTO> atualizarStatus(
            @AuthenticationPrincipal Usuario administradora,
            @PathVariable Long pedidoId,
            @RequestBody @Valid AtualizacaoStatusPedidoDTO dto) {
        return ResponseEntity.ok(
                pedidoGestaoService.atualizarStatus(pedidoId, dto.status(), administradora.getEmail()));
    }
}
