package com.glowup.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.glowup.api.dto.RedefinicaoSenhaDTO;
import com.glowup.api.dto.SolicitacaoOtpDTO;
import com.glowup.api.service.RecuperacaoSenhaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Recuperação de senha", description = "Redefinição de senha por código OTP enviado por e-mail (rotas públicas)")
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class RecuperacaoSenhaController {

    private final RecuperacaoSenhaService recuperacaoService;

    @Operation(summary = "Solicita o código OTP de recuperação",
            description = "A resposta é a mesma para e-mails cadastrados e não cadastrados, para impedir a descoberta de contas.")
    @SecurityRequirements
    @PostMapping("/esqueci-senha")
    public ResponseEntity<Void> solicitarRecuperacao(@RequestBody @Valid SolicitacaoOtpDTO dto) {
        recuperacaoService.solicitarCodigo(dto.email());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Redefine a senha com o código OTP",
            description = "O código tem 6 dígitos, vale por 10 minutos e permite 5 tentativas.")
    @SecurityRequirements
    @PostMapping("/redefinir-senha")
    public ResponseEntity<Void> redefinirSenha(@RequestBody @Valid RedefinicaoSenhaDTO dto) {
        recuperacaoService.redefinirSenha(dto);
        return ResponseEntity.ok().build();
    }
}