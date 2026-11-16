package com.glowup.api.controller;

import com.glowup.api.dto.AutenticacaoDTO;
import com.glowup.api.dto.RefreshRequisicaoDTO;
import com.glowup.api.dto.TokenRespostaDTO;
import com.glowup.api.exception.ErroResposta;
import com.glowup.api.model.Usuario;
import com.glowup.api.service.ProtecaoLoginService;
import com.glowup.api.service.RefreshTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Autenticação", description = "Login, renovação de sessão e logout (rotas públicas)")
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AutenticacaoController {

    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;
    private final ProtecaoLoginService protecaoLogin;

    @Operation(summary = "Autentica a usuária e devolve token e refreshToken",
            description = "O token (curto) vai em Authorization: Bearer <token>. Guarde o refreshToken para renovar a sessão em "
                    + "POST /api/auth/refresh. Se trocaSenhaObrigatoria = true (senha provisória), o front deve abrir a "
                    + "troca de senha: até lá a API só aceita PUT /api/usuarios/me/senha.")
    @SecurityRequirements
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody @Valid AutenticacaoDTO dto) {
        String email = dto.email().trim().toLowerCase(java.util.Locale.ROOT);
        // Bloqueio por conta: vale para qualquer e-mail informado, exista ou não, e antes de checar a senha.
        if (protecaoLogin.estaBloqueado(email)) {
            return ErroResposta.de(HttpStatus.TOO_MANY_REQUESTS,
                    "Muitas tentativas de login para esta conta. Aguarde alguns minutos e tente novamente.");
        }
        var usernamePassword = new UsernamePasswordAuthenticationToken(email, dto.senha());
        try {
            var auth = this.authenticationManager.authenticate(usernamePassword);
            protecaoLogin.registrarSucesso(email);

            // Entidade autenticada (passou pela verificação de senha e ativo=true)
            Usuario usuarioAutenticado = (Usuario) auth.getPrincipal();
            return ResponseEntity.ok(refreshTokenService.iniciarSessao(usuarioAutenticado));
        } catch (BadCredentialsException excecao) {
            protecaoLogin.registrarFalha(email);
            throw excecao;
        }
    }

    @Operation(summary = "Renova a sessão com o refreshToken",
            description = "Cada refreshToken vale uma única vez: a resposta traz um novo par token/refreshToken. "
                    + "Reutilizar um refreshToken já usado encerra todas as sessões da conta. "
                    + "Evite chamadas simultâneas de renovação (ex.: duas abas) com o mesmo refreshToken.")
    @SecurityRequirements
    @PostMapping("/refresh")
    public ResponseEntity<TokenRespostaDTO> renovar(@RequestBody @Valid RefreshRequisicaoDTO dto) {
        return ResponseEntity.ok(refreshTokenService.renovar(dto.refreshToken()));
    }

    @Operation(summary = "Encerra a sessão (revoga o refreshToken)")
    @SecurityRequirements
    @PostMapping("/logout")
    public ResponseEntity<Void> sair(@RequestBody @Valid RefreshRequisicaoDTO dto) {
        refreshTokenService.encerrar(dto.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
