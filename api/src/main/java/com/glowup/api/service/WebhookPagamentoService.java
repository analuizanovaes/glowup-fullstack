package com.glowup.api.service;

import com.glowup.api.domain.Pagamento;
import com.glowup.api.domain.WebhookPagamento;
import com.glowup.api.dto.NotificacaoWebhookDTO;
import com.glowup.api.exception.RecursoNaoEncontradoException;
import com.glowup.api.repository.PagamentoRepository;
import com.glowup.api.repository.WebhookPagamentoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookPagamentoService {

    private static final String TIPO_PAGAMENTO = "payment";

    private final WebhookPagamentoRepository webhookRepository;
    private final PagamentoRepository pagamentoRepository;
    private final PagamentoService pagamentoService;

    public void processar(NotificacaoWebhookDTO notificacao) {
        if (!TIPO_PAGAMENTO.equals(notificacao.tipo()) || notificacao.dataId() == null) {
            return;
        }
        boolean idVeioDoGateway = notificacao.eventoId() != null && !notificacao.eventoId().startsWith("sem-id-");
        if (idVeioDoGateway && webhookRepository.existsByEventoId(notificacao.eventoId())) {
            return;
        }

        // Pagamento ainda não gravado: a resposta não-2xx faz o Mercado Pago reenviar o evento depois.
        Pagamento pagamento = pagamentoRepository.findByIdGateway(notificacao.dataId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pagamento ainda não registrado."));
        Long pedidoId = pagamentoRepository.buscarIdDoPedido(pagamento.getId());

        // Atualizações posteriores (estorno/chargeback) também precisam ser consultadas no gateway.
        pagamentoService.sincronizarPorIdDoGateway(pedidoId, pagamento.getIdGateway(), "WEBHOOK:MERCADOPAGO");
        registrarEvento(notificacao, pagamento);
    }

    private void registrarEvento(NotificacaoWebhookDTO notificacao, Pagamento pagamento) {
        try {
            webhookRepository.saveAndFlush(WebhookPagamento.builder()
                .pagamento(pagamento)
                .eventoId(notificacao.eventoId())
                .tipoEvento(notificacao.acao() != null ? notificacao.acao() : notificacao.tipo())
                .payload(notificacao.payload())
                .processado(true)
                .recebidoEm(LocalDateTime.now())
                .processadoEm(LocalDateTime.now())
                .build());
        } catch (DataIntegrityViolationException duplicadoConcorrente) {
            log.debug("Webhook {} já foi registrado por uma requisição concorrente.", notificacao.eventoId());
        }
    }
}
