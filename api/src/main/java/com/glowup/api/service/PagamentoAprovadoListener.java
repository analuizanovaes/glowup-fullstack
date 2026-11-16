package com.glowup.api.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class PagamentoAprovadoListener {

    private final NotificacaoPedidoService notificacaoPedidoService;

    // Executa somente após o commit: nenhum e-mail é enviado para pagamentos cuja transação sofreu rollback.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoAprovarPagamento(PagamentoAprovadoEvento evento) {
        try {
            notificacaoPedidoService.notificarVendaAprovada(evento.pedidoId());
        } catch (RuntimeException excecao) {
            log.error("Falha ao notificar a venda do pedido {}: {}", evento.pedidoId(), excecao.getMessage());
        }
    }
}
