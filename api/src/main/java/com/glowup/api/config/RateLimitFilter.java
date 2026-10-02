package com.glowup.api.config;

import com.glowup.api.service.RateLimitingService;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitingService rateLimitingService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String uri = request.getRequestURI();
        
        // Aplica o bloqueio apenas nas rotas públicas e sensíveis de acesso/OTP
        if (uri.startsWith("/api/auth/")) {
            String ip = request.getRemoteAddr();
            Bucket bucket = rateLimitingService.resolveBucket(ip);
            
            // Tenta consumir 1 ficha. Se não tiver ficha disponível, bloqueia a requisição.
            if (!bucket.tryConsume(1)) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json");
                response.getWriter().write("{\"erro\": \"Muitas requisições. Por favor, aguarde 15 minutos.\"}");
                return;
            }
        }
        
        filterChain.doFilter(request, response);
    }
}