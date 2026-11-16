package com.glowup.api.service;

import com.glowup.api.dto.TokenRespostaDTO;
import com.glowup.api.model.RefreshToken;
import com.glowup.api.model.Role;
import com.glowup.api.model.Usuario;
import com.glowup.api.repository.RefreshTokenRepository;
import com.glowup.api.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private TokenService tokenService;
    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    private Usuario usuario() {
        return Usuario.builder().id(1L).email("ana@teste.com").role(Role.CLIENT).ativo(true).build();
    }

    private RefreshToken tokenAtivo() {
        return RefreshToken.builder().id(5L).usuarioId(1L).tokenHash("hash")
                .expiraEm(LocalDateTime.now().plusDays(1)).build();
    }

    @BeforeEach
    void configurarChaveDeDerivacao() {
        ReflectionTestUtils.setField(refreshTokenService, "segredoDerivacaoRefresh", "segredo-de-teste-com-mais-de-32-caracteres");
    }

    @Test
    @DisplayName("Renovar troca o par: consome o refresh token e emite um novo")
    void deveRenovarERotacionarOToken() {
        when(refreshTokenRepository.buscarParaRotacao(anyString())).thenReturn(Optional.of(tokenAtivo()));
        when(refreshTokenRepository.revogarSeAtivo(5L)).thenReturn(1);
        Usuario usuario = usuario();
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(tokenService.gerarToken(usuario)).thenReturn("jwt-novo");

        TokenRespostaDTO resposta = refreshTokenService.renovar("refresh-antigo");

        assertEquals("jwt-novo", resposta.token());
        assertNotNull(resposta.refreshToken());
        assertFalse(resposta.trocaSenhaObrigatoria());
        // Um save grava o sucessor e outro atualiza o token antigo com o hash do substituto.
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("Refresh token já usado: encerra todas as sessões da conta")
    void deveEncerrarTodasAsSessoesNaReutilizacao() {
        RefreshToken usado = tokenAtivo();
        usado.setRevogado(true);
        when(refreshTokenRepository.buscarParaRotacao(anyString())).thenReturn(Optional.of(usado));

        assertThrows(BadCredentialsException.class, () -> refreshTokenService.renovar("refresh-antigo"));

        verify(refreshTokenRepository).revogarTodosDoUsuario(1L);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("Repetição imediata do refresh retorna a mesma rotação sem encerrar sessões")
    void deveReutilizarRespostaDuranteJanelaDeTolerancia() {
        when(refreshTokenRepository.buscarParaRotacao(anyString())).thenReturn(Optional.of(tokenAtivo()));
        when(refreshTokenRepository.revogarSeAtivo(5L)).thenReturn(1);
        Usuario usuario = usuario();
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(tokenService.gerarToken(usuario)).thenReturn("jwt-novo");

        TokenRespostaDTO primeira = refreshTokenService.renovar("refresh-antigo");
        TokenRespostaDTO repetida = refreshTokenService.renovar("refresh-antigo");

        assertSame(primeira, repetida);
        verify(refreshTokenRepository).revogarSeAtivo(5L);
        verify(refreshTokenRepository, never()).revogarTodosDoUsuario(1L);
    }

    @Test
    @DisplayName("Refresh duplicado em outra instância reproduz o sucessor persistido")
    void deveReproduzirRotacaoEmOutraInstancia() {
        RefreshToken usado = tokenAtivo();
        when(refreshTokenRepository.buscarParaRotacao(anyString())).thenReturn(Optional.of(usado));
        when(refreshTokenRepository.revogarSeAtivo(5L)).thenReturn(1);
        Usuario usuario = usuario();
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(tokenService.gerarToken(usuario)).thenReturn("jwt-instancia-1", "jwt-instancia-2");

        TokenRespostaDTO primeira = refreshTokenService.renovar("refresh-antigo");
        RefreshTokenService segundaInstancia = new RefreshTokenService(refreshTokenRepository, tokenService, usuarioRepository);
        ReflectionTestUtils.setField(segundaInstancia, "segredoDerivacaoRefresh", "segredo-de-teste-com-mais-de-32-caracteres");
        TokenRespostaDTO repetida = segundaInstancia.renovar("refresh-antigo");

        assertEquals(primeira.refreshToken(), repetida.refreshToken());
        assertEquals("jwt-instancia-2", repetida.token());
        assertTrue(usado.isRevogado());
        assertNotNull(usado.getTokenSubstitutoHash());
        verify(refreshTokenRepository, never()).revogarTodosDoUsuario(1L);
    }

    @Test
    @DisplayName("Refresh token expirado ou desconhecido é recusado")
    void deveRecusarExpiradoEDesconhecido() {
        RefreshToken expirado = tokenAtivo();
        expirado.setExpiraEm(LocalDateTime.now().minusMinutes(1));
        when(refreshTokenRepository.buscarParaRotacao(anyString())).thenReturn(Optional.of(expirado));

        assertThrows(BadCredentialsException.class, () -> refreshTokenService.renovar("qualquer"));
        verify(refreshTokenRepository, never()).revogarSeAtivo(anyLong());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("Conta excluída não renova a sessão")
    void naoDeveRenovarContaExcluida() {
        when(refreshTokenRepository.buscarParaRotacao(anyString())).thenReturn(Optional.of(tokenAtivo()));
        when(refreshTokenRepository.revogarSeAtivo(5L)).thenReturn(1);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class, () -> refreshTokenService.renovar("refresh"));
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("Sessão de conta com senha provisória sinaliza a troca obrigatória")
    void deveSinalizarSenhaProvisoria() {
        Usuario usuario = usuario();
        usuario.setSenhaProvisoria(true);
        when(tokenService.gerarToken(usuario)).thenReturn("jwt");

        TokenRespostaDTO resposta = refreshTokenService.iniciarSessao(usuario);

        assertTrue(resposta.trocaSenhaObrigatoria());
    }
}
