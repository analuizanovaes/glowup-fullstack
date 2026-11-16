package com.glowup.api.controller;

import com.glowup.api.service.ManutencaoService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/manutencao")
public class AdminManutencaoController {

    private final ManutencaoService manutencaoService;

    public AdminManutencaoController(ManutencaoService manutencaoService) {
        this.manutencaoService = manutencaoService;
    }

    @PostMapping("/ativar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> ativar(Authentication authentication, HttpServletRequest request) {
        String adminEmail = authentication.getName();
        String ip = request.getRemoteAddr();
        
        manutencaoService.alterarStatus(true, adminEmail, ip);
        return ResponseEntity.ok(Map.of("mensagem", "Modo de manutenção ATIVADO."));
    }

    @PostMapping("/desativar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> desativar(Authentication authentication, HttpServletRequest request) {
        String adminEmail = authentication.getName();
        String ip = request.getRemoteAddr();
        
        manutencaoService.alterarStatus(false, adminEmail, ip);
        return ResponseEntity.ok(Map.of("mensagem", "Modo de manutenção DESATIVADO."));
    }

    @GetMapping("/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Boolean> verificarStatus() {
        return ResponseEntity.ok(manutencaoService.isModoManutencaoAtivo());
    }
}
