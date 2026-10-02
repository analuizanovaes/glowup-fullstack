package com.glowup.api.controller;

import com.glowup.api.dto.RedefinicaoSenhaDTO;
import com.glowup.api.dto.SolicitacaoOtpDTO;
import com.glowup.api.service.RecuperacaoSenhaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class RecuperacaoSenhaController {

    private final RecuperacaoSenhaService recuperacaoService;

    @PostMapping("/esqueci-senha")
    public ResponseEntity<Void> solicitarRecuperacao(@RequestBody @Valid SolicitacaoOtpDTO dto) {
        recuperacaoService.solicitarCodigo(dto.email());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/redefinir-senha")
    public ResponseEntity<Void> redefinirSenha(@RequestBody @Valid RedefinicaoSenhaDTO dto) {
        recuperacaoService.redefinirSenha(dto);
        return ResponseEntity.ok().build();
    }
}