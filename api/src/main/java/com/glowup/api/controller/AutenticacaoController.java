package com.glowup.api.controller;

import com.glowup.api.model.Usuario;
import com.glowup.api.dto.AutenticacaoDTO;
import com.glowup.api.dto.TokenRespostaDTO;
import com.glowup.api.service.TokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AutenticacaoController {

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;

    @PostMapping("/login")
    public ResponseEntity<TokenRespostaDTO> login(@RequestBody @Valid AutenticacaoDTO dto) {
        var usernamePassword = new UsernamePasswordAuthenticationToken(dto.email(), dto.senha());
        var auth = this.authenticationManager.authenticate(usernamePassword);
        
        // Pega a entidade autenticada (que passou pela verificação de senha e ativo=true)
        Usuario usuarioAutenticado = (Usuario) auth.getPrincipal();
        
        String token = tokenService.gerarToken(usuarioAutenticado);
        return ResponseEntity.ok(new TokenRespostaDTO(token));
    }
}