package com.glowup.api.controller;

import com.glowup.api.domain.StatusAlertaValidade;
import com.glowup.api.domain.StatusReposicao;
import com.glowup.api.dto.AlertaValidadeRespostaDTO;
import com.glowup.api.dto.AnaliseReposicaoRespostaDTO;
import com.glowup.api.dto.NotificacaoAdminRespostaDTO;
import com.glowup.api.dto.StatusReposicaoDTO;
import com.glowup.api.service.CrmAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Tag(name = "Admin - CRM e alertas", description = "Validade, reposição e notificações do painel (somente administradora)")
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminCrmController {

    private final CrmAdminService crmAdminService;

    @Operation(summary = "Alertas de validade em aberto (produtos vencendo em até 25 dias)")
    @GetMapping("/alertas-validade")
    public ResponseEntity<List<AlertaValidadeRespostaDTO>> alertasValidade() {
        return ResponseEntity.ok(crmAdminService.listarAlertasAbertos());
    }

    @Operation(summary = "Marca o alerta como visualizado")
    @PatchMapping("/alertas-validade/{id}/visualizar")
    public ResponseEntity<Void> visualizarAlerta(@PathVariable Long id) {
        crmAdminService.atualizarAlerta(id, StatusAlertaValidade.VISUALIZADO);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Marca o alerta como resolvido")
    @PatchMapping("/alertas-validade/{id}/resolver")
    public ResponseEntity<Void> resolverAlerta(@PathVariable Long id) {
        crmAdminService.atualizarAlerta(id, StatusAlertaValidade.RESOLVIDO);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Sugestões de reposição do CRM (padrão: pendentes)")
    @GetMapping("/reposicoes")
    public ResponseEntity<List<AnaliseReposicaoRespostaDTO>> reposicoes(
            @RequestParam(defaultValue = "PENDENTE") StatusReposicao status) {
        return ResponseEntity.ok(crmAdminService.listarReposicoes(status));
    }

    @Operation(summary = "Atualiza o status de uma sugestão (CONTATADA ou DESCARTADA)")
    @PatchMapping("/reposicoes/{id}/status")
    public ResponseEntity<Void> atualizarReposicao(@PathVariable Long id, @RequestBody @Valid StatusReposicaoDTO dto) {
        crmAdminService.atualizarReposicao(id, dto.status());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Notificações não visualizadas (nova venda, validade, reposição)")
    @GetMapping("/notificacoes")
    public ResponseEntity<List<NotificacaoAdminRespostaDTO>> notificacoes() {
        return ResponseEntity.ok(crmAdminService.listarNotificacoesNaoLidas());
    }

    @Operation(summary = "Marca a notificação como visualizada")
    @PatchMapping("/notificacoes/{id}/visualizar")
    public ResponseEntity<Void> visualizarNotificacao(@PathVariable Long id) {
        crmAdminService.marcarNotificacaoVisualizada(id);
        return ResponseEntity.noContent().build();
    }
}
