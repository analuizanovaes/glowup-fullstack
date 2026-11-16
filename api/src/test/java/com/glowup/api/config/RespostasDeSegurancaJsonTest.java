package com.glowup.api.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RespostasDeSegurancaJsonTest {

    private final RespostasDeSegurancaJson respostas = new RespostasDeSegurancaJson();

    @Test
    @DisplayName("Sem token válido: 401 com corpo JSON contendo status e message")
    void deveResponder401EmJson() throws Exception {
        MockHttpServletResponse resposta = new MockHttpServletResponse();

        respostas.commence(new MockHttpServletRequest(), resposta, new BadCredentialsException("sem token"));

        assertEquals(401, resposta.getStatus());
        assertTrue(resposta.getContentType().startsWith("application/json"));
        assertTrue(resposta.getContentAsString().contains("\"status\":401"));
        assertTrue(resposta.getContentAsString().contains("\"message\":\"Autenticação necessária."));
    }

    @Test
    @DisplayName("Perfil sem permissão: 403 com corpo JSON contendo status e message")
    void deveResponder403EmJson() throws Exception {
        MockHttpServletResponse resposta = new MockHttpServletResponse();

        respostas.handle(new MockHttpServletRequest(), resposta, new AccessDeniedException("sem perfil"));

        assertEquals(403, resposta.getStatus());
        assertTrue(resposta.getContentAsString().contains("\"status\":403"));
        assertTrue(resposta.getContentAsString().contains("Você não tem permissão"));
    }
}
