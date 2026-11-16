package com.glowup.api.config;

import com.glowup.api.model.Role;
import com.glowup.api.model.Usuario;
import com.glowup.api.repository.UsuarioRepository;
import com.glowup.api.service.TokenService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityFilterTest {

    @Mock
    private TokenService tokenService;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private FilterChain cadeia;

    @InjectMocks
    private SecurityFilter filtro;

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    private void autenticarComo(Usuario usuario) {
        when(tokenService.validarToken("t")).thenReturn(usuario.getEmail());
        when(usuarioRepository.findByEmail(usuario.getEmail())).thenReturn(Optional.of(usuario));
        when(tokenService.tokenCorrespondeASenhaAtual("t", usuario)).thenReturn(true);
    }

    private MockHttpServletRequest requisicao(String metodo, String caminho) {
        MockHttpServletRequest requisicao = new MockHttpServletRequest(metodo, caminho);
        requisicao.addHeader("Authorization", "Bearer t");
        return requisicao;
    }

    private Usuario conta(boolean senhaProvisoria) {
        return Usuario.builder().id(1L).email("domingas@glowup.com").role(Role.ADMIN).ativo(true)
                .senhaProvisoria(senhaProvisoria).build();
    }

    @Test
    @DisplayName("Senha provisória: qualquer rota fora da troca de senha responde 403 com código próprio")
    void deveBloquearRotasComSenhaProvisoria() throws Exception {
        autenticarComo(conta(true));
        MockHttpServletResponse resposta = new MockHttpServletResponse();

        filtro.doFilter(requisicao("GET", "/api/admin/pedidos"), resposta, cadeia);

        assertEquals(403, resposta.getStatus());
        assertTrue(resposta.getContentAsString().contains("TROCA_DE_SENHA_OBRIGATORIA"));
        verify(cadeia, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("Senha provisória: troca de senha e perfil continuam liberados")
    void deveLiberarTrocaDeSenhaEPerfil() throws Exception {
        autenticarComo(conta(true));

        filtro.doFilter(requisicao("PUT", "/api/usuarios/me/senha"), new MockHttpServletResponse(), cadeia);
        filtro.doFilter(requisicao("GET", "/api/usuarios/me"), new MockHttpServletResponse(), cadeia);

        verify(cadeia, org.mockito.Mockito.times(2))
                .doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Conta com senha definitiva navega normalmente")
    void deveLiberarContaComSenhaDefinitiva() throws Exception {
        autenticarComo(conta(false));

        filtro.doFilter(requisicao("GET", "/api/admin/pedidos"), new MockHttpServletResponse(), cadeia);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        verify(cadeia).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
