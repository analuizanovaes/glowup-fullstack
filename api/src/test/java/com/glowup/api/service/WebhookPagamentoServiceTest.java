package com.glowup.api.service;

import com.glowup.api.domain.Pagamento;
import com.glowup.api.domain.WebhookPagamento;
import com.glowup.api.dto.NotificacaoWebhookDTO;
import com.glowup.api.exception.RecursoNaoEncontradoException;
import com.glowup.api.repository.PagamentoRepository;
import com.glowup.api.repository.WebhookPagamentoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookPagamentoServiceTest {

    @Mock
    private WebhookPagamentoRepository webhookRepository;
    @Mock
    private PagamentoRepository pagamentoRepository;
    @Mock
    private PagamentoService pagamentoService;

    @InjectMocks
    private WebhookPagamentoService servico;

    private NotificacaoWebhookDTO notificacao(String tipo, String dataId, String eventoId) {
        return new NotificacaoWebhookDTO(tipo, "payment.updated", dataId, eventoId, "{}");
    }

    private Pagamento pagamento() {
        return Pagamento.builder().id(10L).idGateway("mp-1").build();
    }

    @Test
    @DisplayName("Notificações que não são de pagamento são ignoradas")
    void ignoraTipoDiferenteDePagamento() {
        servico.processar(notificacao("merchant_order", "mp-1", "evt-1"));

        verify(pagamentoService, never()).sincronizarPorIdDoGateway(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("Notificação sem data.id é ignorada")
    void ignoraNotificacaoSemDataId() {
        servico.processar(notificacao("payment", null, "evt-1"));

        verify(pagamentoService, never()).sincronizarPorIdDoGateway(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("Evento já processado (mesmo id do gateway) não é processado de novo")
    void ignoraEventoRepetido() {
        when(webhookRepository.existsByEventoId("evt-1")).thenReturn(true);

        servico.processar(notificacao("payment", "mp-1", "evt-1"));

        verify(pagamentoService, never()).sincronizarPorIdDoGateway(any(), anyString(), anyString());
        verify(webhookRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Pagamento ainda não registrado: lança erro para o gateway reenviar depois")
    void pagamentoNaoRegistradoFazOGatewayReenviar() {
        when(webhookRepository.existsByEventoId("evt-1")).thenReturn(false);
        when(pagamentoRepository.findByIdGateway("mp-1")).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class,
                () -> servico.processar(notificacao("payment", "mp-1", "evt-1")));
        verify(webhookRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Notificação válida reconsulta o gateway e registra o evento como processado")
    void sincronizaERegistraOEvento() {
        Pagamento pagamento = pagamento();
        when(webhookRepository.existsByEventoId("evt-1")).thenReturn(false);
        when(pagamentoRepository.findByIdGateway("mp-1")).thenReturn(Optional.of(pagamento));
        when(pagamentoRepository.buscarIdDoPedido(10L)).thenReturn(1L);

        servico.processar(notificacao("payment", "mp-1", "evt-1"));

        verify(pagamentoService).sincronizarPorIdDoGateway(1L, "mp-1", "WEBHOOK:MERCADOPAGO");
        ArgumentCaptor<WebhookPagamento> registrado = ArgumentCaptor.forClass(WebhookPagamento.class);
        verify(webhookRepository).saveAndFlush(registrado.capture());
        assertEquals("evt-1", registrado.getValue().getEventoId());
        assertTrue(registrado.getValue().isProcessado());
    }

    @Test
    @DisplayName("Evento sem id do gateway (sem-id-...) é sempre reprocessado, sem consultar duplicidade")
    void eventoSemIdDoGatewayNaoConsultaDuplicidade() {
        when(pagamentoRepository.findByIdGateway("mp-1")).thenReturn(Optional.of(pagamento()));
        when(pagamentoRepository.buscarIdDoPedido(10L)).thenReturn(1L);

        servico.processar(notificacao("payment", "mp-1", "sem-id-abc"));

        verify(webhookRepository, never()).existsByEventoId(anyString());
        verify(pagamentoService).sincronizarPorIdDoGateway(1L, "mp-1", "WEBHOOK:MERCADOPAGO");
    }

    @Test
    @DisplayName("Duas entregas simultâneas do mesmo evento: a duplicada não derruba a requisição")
    void duplicidadeConcorrenteNaoLancaErro() {
        when(webhookRepository.existsByEventoId("evt-1")).thenReturn(false);
        when(pagamentoRepository.findByIdGateway("mp-1")).thenReturn(Optional.of(pagamento()));
        when(pagamentoRepository.buscarIdDoPedido(10L)).thenReturn(1L);
        when(webhookRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicado"));

        assertDoesNotThrow(() -> servico.processar(notificacao("payment", "mp-1", "evt-1")));
    }
}
