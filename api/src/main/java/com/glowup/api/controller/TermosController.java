package com.glowup.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Tag(name = "Termos", description = "Versão vigente dos Termos de Uso e da Política de Privacidade (RD04)")
@RequestMapping("/api/termos")
public class TermosController {

    private final String versaoAtual;

    public TermosController(@Value("${glowup.termos.versao-atual:1.0}") String versaoAtual) {
        this.versaoAtual = versaoAtual;
    }

    @Operation(summary = "Versão atual dos termos",
            description = "O front envia este valor em versaoTermos no cadastro e no checkout.")
    @SecurityRequirements
    @GetMapping("/versao-atual")
    public ResponseEntity<Map<String, String>> versaoAtual() {
        return ResponseEntity.ok(Map.of("versao", versaoAtual));
    }
}
