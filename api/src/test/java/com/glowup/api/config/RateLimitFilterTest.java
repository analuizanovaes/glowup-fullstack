package com.glowup.api.config;

import com.glowup.api.service.RateLimitingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RateLimitFilterTest {

    private RateLimitFilter filtro;

    @BeforeEach
    void preparar() {
        // auth 2, pedidos 30, cep 100, janela 15 min, sincronizar 2 por minuto
        filtro = new RateLimitFilter(new RateLimitingService(2, 30, 100, 15, 2));
    }

    private int chamar(String metodo, String uri, String ip, String forwardedFor) throws Exception {
        MockHttpServletRequest requisicao = new MockHttpServletRequest(metodo, uri);
        requisicao.setRemoteAddr(ip);
        if (forwardedFor != null) {
            requisicao.addHeader("X-Forwarded-For", forwardedFor);
        }
        MockHttpServletResponse resposta = new MockHttpServletResponse();
        filtro.doFilter(requisicao, resposta, new MockFilterChain());
        return resposta.getStatus();
    }

    @Test
    @DisplayName("Login: a 3ª tentativa do mesmo IP na janela recebe 429")
    void loginBloqueiaDepoisDoLimite() throws Exception {
        assertEquals(200, chamar("POST", "/api/auth/login", "1.1.1.1", null));
        assertEquals(200, chamar("POST", "/api/auth/login", "1.1.1.1", null));
        assertEquals(429, chamar("POST", "/api/auth/login", "1.1.1.1", null));
    }

    @Test
    @DisplayName("IPs diferentes têm limites independentes")
    void ipsDiferentesNaoSeAfetam() throws Exception {
        chamar("POST", "/api/auth/login", "1.1.1.1", null);
        chamar("POST", "/api/auth/login", "1.1.1.1", null);

        assertEquals(429, chamar("POST", "/api/auth/login", "1.1.1.1", null));
        assertEquals(200, chamar("POST", "/api/auth/login", "2.2.2.2", null));
    }

    @Test
    @DisplayName("Sincronizar pagamento tem limite próprio por minuto")
    void sincronizarPagamentoTemLimite() throws Exception {
        String uri = "/api/pagamentos/pedido/5/sincronizar";
        assertEquals(200, chamar("POST", uri, "1.1.1.1", null));
        assertEquals(200, chamar("POST", uri, "1.1.1.1", null));
        assertEquals(429, chamar("POST", uri, "1.1.1.1", null));
        assertEquals(200, chamar("POST", uri, "3.3.3.3", null));
    }

    @Test
    @DisplayName("Consultar o pagamento (GET) não entra no limite de sincronização")
    void consultaDePagamentoNaoTemLimite() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertEquals(200, chamar("GET", "/api/pagamentos/pedido/5", "1.1.1.1", null));
        }
    }

    @Test
    @DisplayName("Webhook do Mercado Pago tem teto por minuto e por IP (padrão 120)")
    void webhookTemLimite() throws Exception {
        for (int i = 0; i < 120; i++) {
            assertEquals(200, chamar("POST", "/api/webhooks/mercadopago", "1.1.1.1", null));
        }
        assertEquals(429, chamar("POST", "/api/webhooks/mercadopago", "1.1.1.1", null));
        assertEquals(200, chamar("POST", "/api/webhooks/mercadopago", "4.4.4.4", null));
    }

    @Test
    @DisplayName("Rotas sem limite nunca recebem 429")
    void rotasSemLimite() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertEquals(200, chamar("GET", "/api/health", "1.1.1.1", null));
        }
    }

    @Test
    @DisplayName("Por padrão ignora o X-Forwarded-For forjado e usa o IP resolvido pelo servidor")
    void ignoraForwardedForPorPadrao() throws Exception {
        chamar("POST", "/api/auth/login", "10.0.0.1", "8.8.8.1");
        chamar("POST", "/api/auth/login", "10.0.0.1", "8.8.8.2");

        assertEquals(429, chamar("POST", "/api/auth/login", "10.0.0.1", "8.8.8.3"));
    }

    @Test
    @DisplayName("Plano B ligado: usa a última entrada do X-Forwarded-For (a do proxy), separando clientes atrás do mesmo proxy")
    void usaUltimoForwardedForQuandoLigado() throws Exception {
        ReflectionTestUtils.setField(filtro, "ipPeloUltimoForwardedFor", true);

        // A primeira entrada pode ser forjada pela cliente; só a última (do proxy) conta.
        chamar("POST", "/api/auth/login", "10.0.0.1", "forjado-1, 200.1.1.1");
        chamar("POST", "/api/auth/login", "10.0.0.1", "forjado-2, 200.1.1.1");

        assertEquals(429, chamar("POST", "/api/auth/login", "10.0.0.1", "forjado-3, 200.1.1.1"));
        assertEquals(200, chamar("POST", "/api/auth/login", "10.0.0.1", "qualquer, 200.2.2.2"));
    }
}
