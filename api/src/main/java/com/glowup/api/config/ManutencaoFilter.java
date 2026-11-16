package com.glowup.api.config;

import com.glowup.api.service.ManutencaoService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class ManutencaoFilter extends OncePerRequestFilter {

    private final ManutencaoService manutencaoService;

    public ManutencaoFilter(ManutencaoService manutencaoService) {
        this.manutencaoService = manutencaoService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();

        if (manutencaoService.isModoManutencaoAtivo()) {
            // Mantém o webhook acessível para processar atualizações de pagamento durante a manutenção.
            // A administradora precisa continuar usando o painel (perfil, catálogo, pedidos) com a loja fechada.
            // Este filtro roda depois do SecurityFilter, então a autenticação já está no contexto.
            org.springframework.security.core.Authentication autenticacao =
                    org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            boolean administradora = autenticacao != null && autenticacao.isAuthenticated()
                    && autenticacao.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));

            boolean isAllowedPath = administradora
                    || path.startsWith("/api/admin")
                    || path.startsWith("/api/auth")
                    || path.startsWith("/api/health")
                    || path.startsWith("/api/webhooks");

            if (!isAllowedPath) {
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                response.setContentType("application/json;charset=UTF-8");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write("{\"status\":503,\"message\":\"Sistema em manutenção temporária.\","
                        + "\"erro\":\"Sistema em manutenção temporária.\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
