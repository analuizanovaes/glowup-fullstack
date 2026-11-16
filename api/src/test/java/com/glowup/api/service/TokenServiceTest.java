package com.glowup.api.service;

import com.glowup.api.model.Role;
import com.glowup.api.model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenServiceTest {

    private TokenService tokenService;
    private Usuario usuario;

    @BeforeEach
    void preparar() {
        tokenService = new TokenService();
        ReflectionTestUtils.setField(tokenService, "secret", "segredo-de-teste-com-mais-de-32-caracteres-ok");
        usuario = Usuario.builder().id(1L).email("ana@teste.com").senhaHash("hash-antigo").role(Role.ADMIN).build();
    }

    @Test
    @DisplayName("Token válido resolve o e-mail e corresponde à senha atual")
    void tokenValidoCorrespondeASenhaAtual() {
        String token = tokenService.gerarToken(usuario);

        assertEquals("ana@teste.com", tokenService.validarToken(token));
        assertTrue(tokenService.tokenCorrespondeASenhaAtual(token, usuario));
    }

    @Test
    @DisplayName("Depois de trocar a senha, o token antigo deixa de valer")
    void tokenAntigoInvalidaAposTrocaDeSenha() {
        String tokenAntigo = tokenService.gerarToken(usuario);

        usuario.setSenhaHash("hash-novo");

        assertFalse(tokenService.tokenCorrespondeASenhaAtual(tokenAntigo, usuario));
        assertTrue(tokenService.tokenCorrespondeASenhaAtual(tokenService.gerarToken(usuario), usuario));
    }

    @Test
    @DisplayName("Token adulterado é rejeitado")
    void tokenAdulteradoERejeitado() {
        String token = tokenService.gerarToken(usuario);

        assertFalse(tokenService.tokenCorrespondeASenhaAtual(token + "x", usuario));
        assertEquals("", tokenService.validarToken("lixo"));
    }
}
