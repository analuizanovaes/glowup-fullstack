package com.glowup.api.service;

import com.glowup.api.dto.RedefinicaoSenhaDTO;
import com.glowup.api.exception.CodigoOtpInvalidoException;
import com.glowup.api.model.RecuperacaoSenha;
import com.glowup.api.model.Usuario;
import com.glowup.api.repository.RecuperacaoSenhaRepository;
import com.glowup.api.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecuperacaoSenhaServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private RecuperacaoSenhaRepository recuperacaoRepository;
    @Mock
    private EmailService emailService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private RecuperacaoSenhaService recuperacaoSenhaService;

    private Usuario criarUsuario() {
        return Usuario.builder().id(1L).nome("Ana").email("ana@teste.com").senhaHash("hash-antigo").build();
    }

    private RecuperacaoSenha criarRecuperacao(Usuario usuario, LocalDateTime expiracao, int tentativas) {
        return RecuperacaoSenha.builder()
                .codigoOtpHash("hash-do-codigo")
                .expiracao(expiracao)
                .tentativas(tentativas)
                .usuario(usuario)
                .build();
    }

    private void simularRecuperacaoAtiva(Usuario usuario, RecuperacaoSenha recuperacao) {
        when(usuarioRepository.findByEmail("ana@teste.com")).thenReturn(Optional.of(usuario));
        when(recuperacaoRepository.findFirstByUsuarioIdAndUtilizadoFalseOrderByCriadoEmDesc(1L))
                .thenReturn(Optional.of(recuperacao));
    }

    @Test
    @DisplayName("E-mail inexistente: nada é gerado nem enviado (sem revelar se a conta existe)")
    void naoDeveGerarCodigoParaEmailInexistente() {
        when(usuarioRepository.findByEmail("fantasma@teste.com")).thenReturn(Optional.empty());

        recuperacaoSenhaService.solicitarCodigo(" Fantasma@Teste.com ");

        verify(recuperacaoRepository, never()).save(any());
        verify(emailService, never()).enviarEmailTexto(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Gera código de 6 dígitos, grava só o hash e envia o código por e-mail")
    void deveGravarApenasOHashDoCodigo() {
        when(usuarioRepository.findByEmail("ana@teste.com")).thenReturn(Optional.of(criarUsuario()));
        when(passwordEncoder.encode(anyString())).thenReturn("hash-do-codigo");

        recuperacaoSenhaService.solicitarCodigo("ana@teste.com");

        ArgumentCaptor<RecuperacaoSenha> recuperacaoSalva = ArgumentCaptor.forClass(RecuperacaoSenha.class);
        ArgumentCaptor<String> mensagem = ArgumentCaptor.forClass(String.class);
        verify(recuperacaoRepository).invalidarCodigosAtivos(eq(1L), any(LocalDateTime.class));
        verify(recuperacaoRepository).save(recuperacaoSalva.capture());
        verify(emailService).enviarEmailTexto(eq("ana@teste.com"), anyString(), mensagem.capture());

        assertEquals("hash-do-codigo", recuperacaoSalva.getValue().getCodigoOtpHash());
        assertTrue(recuperacaoSalva.getValue().getExpiracao().isAfter(LocalDateTime.now()));
        assertTrue(mensagem.getValue().matches("(?s).*\\b\\d{6}\\b.*"));
        assertFalse(mensagem.getValue().contains(recuperacaoSalva.getValue().getCodigoOtpHash()));
    }

    @Test
    @DisplayName("Código correto redefine a senha e consome o código")
    void deveRedefinirSenhaComCodigoCorreto() {
        Usuario usuario = criarUsuario();
        RecuperacaoSenha recuperacao = criarRecuperacao(usuario, LocalDateTime.now().plusMinutes(5), 0);
        simularRecuperacaoAtiva(usuario, recuperacao);
        when(passwordEncoder.matches("123456", "hash-do-codigo")).thenReturn(true);
        when(passwordEncoder.encode("NovaSenha@1")).thenReturn("hash-novo");

        recuperacaoSenhaService.redefinirSenha(new RedefinicaoSenhaDTO("ana@teste.com", "123456", "NovaSenha@1"));

        assertEquals("hash-novo", usuario.getSenhaHash());
        assertTrue(recuperacao.isUtilizado());
        assertNotNull(recuperacao.getUtilizadoEm());
    }

    @Test
    @DisplayName("Código errado conta uma tentativa e não altera a senha")
    void deveContarTentativaErrada() {
        Usuario usuario = criarUsuario();
        RecuperacaoSenha recuperacao = criarRecuperacao(usuario, LocalDateTime.now().plusMinutes(5), 0);
        simularRecuperacaoAtiva(usuario, recuperacao);
        when(passwordEncoder.matches("000000", "hash-do-codigo")).thenReturn(false);

        assertThrows(CodigoOtpInvalidoException.class,
                () -> recuperacaoSenhaService.redefinirSenha(new RedefinicaoSenhaDTO("ana@teste.com", "000000", "NovaSenha@1")));

        assertEquals(1, recuperacao.getTentativas());
        assertFalse(recuperacao.isUtilizado());
        assertEquals("hash-antigo", usuario.getSenhaHash());
    }

    @Test
    @DisplayName("A 5ª tentativa errada invalida o código")
    void deveInvalidarCodigoNaQuintaTentativaErrada() {
        Usuario usuario = criarUsuario();
        RecuperacaoSenha recuperacao = criarRecuperacao(usuario, LocalDateTime.now().plusMinutes(5), 4);
        simularRecuperacaoAtiva(usuario, recuperacao);
        when(passwordEncoder.matches("000000", "hash-do-codigo")).thenReturn(false);

        assertThrows(CodigoOtpInvalidoException.class,
                () -> recuperacaoSenhaService.redefinirSenha(new RedefinicaoSenhaDTO("ana@teste.com", "000000", "NovaSenha@1")));

        assertEquals(5, recuperacao.getTentativas());
        assertTrue(recuperacao.isUtilizado());
    }

    @Test
    @DisplayName("Código expirado ou já bloqueado é recusado sem nem conferir o hash")
    void deveRecusarCodigoExpiradoOuBloqueado() {
        Usuario usuario = criarUsuario();
        RecuperacaoSenha expirada = criarRecuperacao(usuario, LocalDateTime.now().minusMinutes(1), 0);
        simularRecuperacaoAtiva(usuario, expirada);

        assertThrows(CodigoOtpInvalidoException.class,
                () -> recuperacaoSenhaService.redefinirSenha(new RedefinicaoSenhaDTO("ana@teste.com", "123456", "NovaSenha@1")));

        RecuperacaoSenha bloqueada = criarRecuperacao(usuario, LocalDateTime.now().plusMinutes(5), 5);
        when(recuperacaoRepository.findFirstByUsuarioIdAndUtilizadoFalseOrderByCriadoEmDesc(1L))
                .thenReturn(Optional.of(bloqueada));

        assertThrows(CodigoOtpInvalidoException.class,
                () -> recuperacaoSenhaService.redefinirSenha(new RedefinicaoSenhaDTO("ana@teste.com", "123456", "NovaSenha@1")));

        verify(passwordEncoder, never()).matches(anyString(), anyString());
        assertNotEquals("hash-novo", usuario.getSenhaHash());
    }

    @Test
    @DisplayName("Redefinição para e-mail inexistente recebe o mesmo erro genérico")
    void deveRecusarRedefinicaoParaEmailInexistente() {
        when(usuarioRepository.findByEmail("fantasma@teste.com")).thenReturn(Optional.empty());

        assertThrows(CodigoOtpInvalidoException.class,
                () -> recuperacaoSenhaService.redefinirSenha(new RedefinicaoSenhaDTO("fantasma@teste.com", "123456", "NovaSenha@1")));
    }

    @Test
    @DisplayName("Novo pedido de código em menos de 60 s não envia outro e-mail (mesma resposta, sem enchente de e-mails)")
    void naoReenviaCodigoDentroDoIntervaloMinimo() {
        Usuario usuario = criarUsuario();
        RecuperacaoSenha recente = criarRecuperacao(usuario, LocalDateTime.now().plusMinutes(9), 0);
        recente.setCriadoEm(LocalDateTime.now().minusSeconds(10));
        when(usuarioRepository.findByEmail("ana@teste.com")).thenReturn(Optional.of(usuario));
        when(recuperacaoRepository.findFirstByUsuarioIdAndUtilizadoFalseOrderByCriadoEmDesc(1L))
                .thenReturn(Optional.of(recente));

        recuperacaoSenhaService.solicitarCodigo("ana@teste.com");

        verify(emailService, never()).enviarEmailTexto(anyString(), anyString(), anyString());
        verify(recuperacaoRepository, never()).save(any(RecuperacaoSenha.class));
        verify(recuperacaoRepository, never()).invalidarCodigosAtivos(any(), any());
    }

    @Test
    @DisplayName("Depois de 60 s um novo código pode ser emitido")
    void reenviaCodigoDepoisDoIntervaloMinimo() {
        Usuario usuario = criarUsuario();
        RecuperacaoSenha antiga = criarRecuperacao(usuario, LocalDateTime.now().plusMinutes(5), 0);
        antiga.setCriadoEm(LocalDateTime.now().minusSeconds(120));
        when(usuarioRepository.findByEmail("ana@teste.com")).thenReturn(Optional.of(usuario));
        when(recuperacaoRepository.findFirstByUsuarioIdAndUtilizadoFalseOrderByCriadoEmDesc(1L))
                .thenReturn(Optional.of(antiga));
        when(passwordEncoder.encode(anyString())).thenReturn("hash-do-codigo");

        recuperacaoSenhaService.solicitarCodigo("ana@teste.com");

        verify(emailService).enviarEmailTexto(eq("ana@teste.com"), anyString(), anyString());
    }
}
