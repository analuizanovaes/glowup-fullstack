package com.glowup.api.controller;

import com.glowup.api.dto.NotificacaoWebhookDTO;
import com.glowup.api.service.AssinaturaWebhookValidador;
import com.glowup.api.service.WebhookPagamentoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.ArgumentCaptor;

class WebhookMercadoPagoControllerTest {

    private WebhookPagamentoService webhookService;
    private AssinaturaWebhookValidador validador;
    private WebhookMercadoPagoController controller;

    @BeforeEach
    void preparar() {
        webhookService = mock(WebhookPagamentoService.class);
        validador = mock(AssinaturaWebhookValidador.class);
        controller = new WebhookMercadoPagoController(webhookService, validador, JsonMapper.builder().build());
    }

    @Test
    @DisplayName("Assinatura inválida: 401 e nada é processado")
    void assinaturaInvalidaResponde401() {
        when(validador.validar("ts=1,v1=x", "123", "req-1")).thenReturn(false);

        ResponseEntity<Void> resposta = controller.receberNotificacao(
                Map.of("data", Map.of("id", "123")), "123", "payment", "ts=1,v1=x", "req-1");

        assertEquals(HttpStatus.UNAUTHORIZED, resposta.getStatusCode());
        verify(webhookService, never()).processar(any());
    }

    @Test
    @DisplayName("Sem cabeçalho de assinatura: 401")
    void semAssinaturaResponde401() {
        when(validador.validar(null, "123", null)).thenReturn(false);

        ResponseEntity<Void> resposta = controller.receberNotificacao(
                Map.of("data", Map.of("id", "123")), "123", "payment", null, null);

        assertEquals(HttpStatus.UNAUTHORIZED, resposta.getStatusCode());
        verify(webhookService, never()).processar(any());
    }

    @Test
    @DisplayName("Assinatura válida: 200 e a notificação segue para o serviço com o data.id da URL")
    void assinaturaValidaProcessaENotifica() {
        when(validador.validar("ts=1,v1=ok", "123", "req-1")).thenReturn(true);

        ResponseEntity<Void> resposta = controller.receberNotificacao(
                Map.of("id", 777, "action", "payment.updated", "data", Map.of("id", "123")),
                "123", "payment", "ts=1,v1=ok", "req-1");

        assertEquals(HttpStatus.OK, resposta.getStatusCode());
        ArgumentCaptor<NotificacaoWebhookDTO> captor = ArgumentCaptor.forClass(NotificacaoWebhookDTO.class);
        verify(webhookService).processar(captor.capture());
        assertEquals("payment", captor.getValue().tipo());
        assertEquals("payment.updated", captor.getValue().acao());
        assertEquals("123", captor.getValue().dataId());
        assertEquals("777", captor.getValue().eventoId());
    }

    @Test
    @DisplayName("Sem data.id na URL, o id vem do corpo; sem id de evento, é gerado um identificador estável sem-id-...")
    void idVemDoCorpoEEventoSemIdGeraHash() {
        when(validador.validar(eq("ts=1,v1=ok"), eq("456"), eq("req-2"))).thenReturn(true);
        Map<String, Object> corpo = Map.of("type", "payment", "action", "payment.created", "data", Map.of("id", "456"));

        controller.receberNotificacao(corpo, null, null, "ts=1,v1=ok", "req-2");
        controller.receberNotificacao(corpo, null, null, "ts=1,v1=ok", "req-2");

        ArgumentCaptor<NotificacaoWebhookDTO> captor = ArgumentCaptor.forClass(NotificacaoWebhookDTO.class);
        verify(webhookService, org.mockito.Mockito.times(2)).processar(captor.capture());
        NotificacaoWebhookDTO primeira = captor.getAllValues().get(0);
        assertEquals("456", primeira.dataId());
        assertEquals("payment", primeira.tipo());
        assertTrue(primeira.eventoId().startsWith("sem-id-"));
        assertEquals(primeira.eventoId(), captor.getAllValues().get(1).eventoId());
    }
}
