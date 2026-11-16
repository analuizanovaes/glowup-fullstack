package com.glowup.api.controller;

import com.glowup.api.dto.ResumoDashboardDTO;
import com.glowup.api.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
@Tag(name = "Admin - Dashboard", description = "Métricas consolidadas de vendas e Lucro Real (perfil ADMIN)")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/resumo")
    @Operation(summary = "Resumo financeiro do período (padrão: mês corrente). Datas no formato AAAA-MM-DD")
    public ResponseEntity<ResumoDashboardDTO> obterResumo(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return ResponseEntity.ok(dashboardService.gerarResumo(inicio, fim));
    }
}
