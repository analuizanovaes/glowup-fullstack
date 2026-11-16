package com.glowup.api.config;

import com.glowup.api.service.RateLimitingService;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    /** Rotas de autenticação com limite próprio; qualquer outra rota sob /api/auth/ divide um único balde. */
    private static final Set<String> ENDPOINTS_AUTH = Set.of(
            "/api/auth/login", "/api/auth/logout", "/api/auth/esqueci-senha", "/api/auth/redefinir-senha");

    private final RateLimitingService rateLimitingService;

    /**
     * Diagnóstico do primeiro deploy: com true, registra no log o IP resolvido e o X-Forwarded-For de cada
     * requisição limitada, para conferir se o proxy está entregando o IP real de cada cliente.
     */
    @Value("${glowup.ratelimit.logar-ip:false}")
    private boolean logarIp;

    /**
     * Plano B se o getRemoteAddr() continuar igual para todo mundo atrás do proxy: usa a ÚLTIMA entrada do
     * X-Forwarded-For (a que o próprio proxy da plataforma acrescenta; a cliente não consegue forjá-la).
     * Ligue só se o proxy for único e confiável.
     */
    @Value("${glowup.ratelimit.ip-pelo-ultimo-forwarded-for:false}")
    private boolean ipPeloUltimoForwardedFor;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        Bucket bucket = escolherBalde(request);

        // Tenta consumir 1 ficha. Sem ficha disponível, bloqueia a requisição.
        if (bucket != null && !bucket.tryConsume(1)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json;charset=UTF-8");
            String mensagem = "Muitas requisições. Por favor, aguarde alguns minutos.";
            response.getWriter().write("{\"status\":429,\"message\":\"" + mensagem
                    + "\",\"erro\":\"" + mensagem + "\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /** Define qual limite vale para a rota; null = rota sem limite. */
    private Bucket escolherBalde(HttpServletRequest request) {
        String uri = caminhoDecodificado(request);
        String metodo = request.getMethod();
        String ip = resolverIp(request);

        if ("POST".equalsIgnoreCase(metodo) && uri.startsWith("/api/pagamentos/pedido/") && uri.endsWith("/sincronizar")) {
            return rateLimitingService.resolveBucketSincronizacao(ip);
        }
        if ("POST".equalsIgnoreCase(metodo) && uri.equals("/api/webhooks/mercadopago")) {
            return rateLimitingService.resolveBucketWebhook(ip);
        }
        if (uri.equals("/api/auth/refresh")) {
            return rateLimitingService.resolveBucket("refresh:" + ip);
        }
        if (uri.startsWith("/api/auth/")) {
            String endpointAuth = ENDPOINTS_AUTH.contains(uri) ? uri : "outros";
            return rateLimitingService.resolveBucket("auth:" + endpointAuth + ":" + ip);
        }
        if (uri.equals("/api/usuarios/me/senha")) {
            return rateLimitingService.resolveBucket("senha:" + ip);
        }
        if (uri.equals("/api/usuarios") && "POST".equalsIgnoreCase(metodo)) {
            return rateLimitingService.resolveBucket("cadastro:" + ip);
        }
        if ("POST".equalsIgnoreCase(metodo) && (uri.equals("/api/pedidos/checkout") || uri.equals("/api/pagamentos"))) {
            return rateLimitingService.resolveBucketPedidos(ip);
        }
        if ("GET".equalsIgnoreCase(metodo) && uri.startsWith("/api/logistica/cep/")) {
            return rateLimitingService.resolveBucketCep(ip);
        }
        return null;
    }

    /** Caminho já decodificado e normalizado pelo contêiner: variações como /api/auth/%6cogin caem no mesmo limite. */
    private String caminhoDecodificado(HttpServletRequest request) {
        String servletPath = request.getServletPath() == null ? "" : request.getServletPath();
        String pathInfo = request.getPathInfo() == null ? "" : request.getPathInfo();
        String caminho = servletPath + pathInfo;
        return caminho.isEmpty() ? request.getRequestURI() : caminho;
    }

    private String resolverIp(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        String encaminhado = request.getHeader("X-Forwarded-For");
        if (ipPeloUltimoForwardedFor && encaminhado != null && !encaminhado.isBlank()) {
            String[] entradas = encaminhado.split(",");
            String ultimo = entradas[entradas.length - 1].trim();
            if (!ultimo.isEmpty()) {
                ip = ultimo;
            }
        }
        if (logarIp) {
            log.info("RateLimit IP resolvido={} remoteAddr={} X-Forwarded-For={} uri={}",
                    ip, request.getRemoteAddr(), encaminhado, request.getRequestURI());
        }
        return ip;
    }
}
