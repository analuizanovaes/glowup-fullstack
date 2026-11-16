package com.glowup.api.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Respostas JSON para as falhas barradas pelo filtro de segurança (401 sem token válido e 403 sem perfil),
 * que ocorrem antes do controller e, portanto, fora do alcance dos @RestControllerAdvice.
 */
@Component
public class RespostasDeSegurancaJson implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final String MENSAGEM_NAO_AUTENTICADO =
            "Autenticação necessária. Informe um token JWT válido no cabeçalho Authorization.";
    private static final String MENSAGEM_SEM_PERMISSAO = "Você não tem permissão para acessar este recurso.";

    @Override
    public void commence(HttpServletRequest requisicao, HttpServletResponse resposta,
                         AuthenticationException excecao) throws IOException {
        escrever(resposta, HttpServletResponse.SC_UNAUTHORIZED, MENSAGEM_NAO_AUTENTICADO);
    }

    @Override
    public void handle(HttpServletRequest requisicao, HttpServletResponse resposta,
                       AccessDeniedException excecao) throws IOException {
        escrever(resposta, HttpServletResponse.SC_FORBIDDEN, MENSAGEM_SEM_PERMISSAO);
    }

    // As mensagens são constantes sem aspas, por isso não exigem escape de JSON.
    private void escrever(HttpServletResponse resposta, int status, String mensagem) throws IOException {
        resposta.setStatus(status);
        resposta.setContentType("application/json;charset=UTF-8");
        resposta.getWriter().write(String.format("{\"status\":%d,\"message\":\"%s\",\"erro\":\"%s\"}", status, mensagem, mensagem));
    }
}
