package com.glowup.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Garante as regras de acesso por perfil. Sobe o contexto completo, então precisa do PostgreSQL (o CI fornece). */
@SpringBootTest
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class AutorizacaoRotasTest {

    private final WebApplicationContext contexto;

    AutorizacaoRotasTest(WebApplicationContext contexto) {
        this.contexto = contexto;
    }

    private MockMvc mvc;

    @BeforeEach
    void configurar() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("Sem token: rota de admin responde 401")
    void semTokenNaRotaAdminDeve401() throws Exception {
        mvc.perform(get("/api/admin/pedidos")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Cliente logada: rota de admin responde 403")
    void clienteNaRotaAdminDeve403() throws Exception {
        mvc.perform(get("/api/admin/pedidos").with(user("cliente@teste.com").roles("CLIENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Sem token: catálogo e pedidos exigem login (401)")
    void semTokenNoCatalogoDeve401() throws Exception {
        mvc.perform(get("/api/produtos")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/pedidos/meus")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Health check e versão dos termos são públicos")
    void rotasPublicasDevemResponder200() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk());
        mvc.perform(get("/api/termos/versao-atual")).andExpect(status().isOk());
    }
}
