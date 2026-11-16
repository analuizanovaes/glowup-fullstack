package com.glowup.api.config;

import com.glowup.api.repository.UsuarioRepository;
import com.glowup.api.service.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class SecurityFilter extends OncePerRequestFilter {

    private final TokenService tokenService;
    private final UsuarioRepository usuarioRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        var token = recuperarToken(request);

        if (token != null) {
            var email = tokenService.validarToken(token);
            if (!email.isEmpty()) {
                var usuario = usuarioRepository.findByEmail(email).orElse(null);
                
                if (usuario != null && usuario.isEnabled() && tokenService.tokenCorrespondeASenhaAtual(token, usuario)) {
                    if (usuario.isSenhaProvisoria() && !permitidoComSenhaProvisoria(request)) {
                        responderTrocaDeSenhaObrigatoria(response);
                        return;
                    }
                    var authentication = new UsernamePasswordAuthenticationToken(usuario, null, usuario.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }
        
        filterChain.doFilter(request, response);
    }

    /** Com senha provisória só passam: ver o perfil, trocar a senha e renovar/encerrar a sessão. */
    private boolean permitidoComSenhaProvisoria(HttpServletRequest request) {
        String caminho = request.getRequestURI();
        String metodo = request.getMethod();
        return caminho.startsWith("/api/auth/")
                || ("PUT".equalsIgnoreCase(metodo) && caminho.equals("/api/usuarios/me/senha"))
                || ("GET".equalsIgnoreCase(metodo) && caminho.equals("/api/usuarios/me"));
    }

    private void responderTrocaDeSenhaObrigatoria(HttpServletResponse response) throws IOException {
        String mensagem = "Troque a senha provisória em PUT /api/usuarios/me/senha para continuar.";
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"status\":403,\"message\":\"" + mensagem + "\",\"erro\":\"" + mensagem
                + "\",\"codigo\":\"TROCA_DE_SENHA_OBRIGATORIA\"}");
    }

    private String recuperarToken(HttpServletRequest request) {
        var authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        return authHeader.substring("Bearer ".length()).trim();
    }
}
