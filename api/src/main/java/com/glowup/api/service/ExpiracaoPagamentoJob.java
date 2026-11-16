package com.glowup.api.service;

import com.glowup.api.domain.MetodoPagamento;
import com.glowup.api.domain.StatusPagamento;
import com.glowup.api.domain.StatusPedido;
import com.glowup.api.repository.PagamentoRepository;
import com.glowup.api.repository.PedidoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Libera o estoque de pedidos que não foram pagos: Pix expirado e pedidos que nunca chegaram
 * a iniciar o pagamento (por exemplo, quando o gateway esteve fora do ar).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExpiracaoPagamentoJob {

    private final PagamentoRepository pagamentoRepository;
    private final PedidoRepository pedidoRepository;
    private final PagamentoService pagamentoService;
    private final ConciliacaoPagamentoService conciliacaoService;

    @Value("${glowup.pagamento.reserva-minutos:30}")
    private long reservaMinutos;

    @Scheduled(fixedDelayString = "${glowup.pagamento.varredura-ms:300000}")
    public void encerrarPagamentosExpirados() {
        encerrarPixExpirados();
        cancelarPedidosSemPagamento();
    }

    private void encerrarPixExpirados() {
        List<Long> pedidos = pagamentoRepository.buscarPedidosComPagamentoExpirado(
                MetodoPagamento.PIX, StatusPagamento.PENDENTE, LocalDateTime.now());
        for (Long pedidoId : pedidos) {
            try {
                pagamentoService.encerrarPixExpirado(pedidoId);
            } catch (RuntimeException excecao) {
                log.warn("Pix do pedido {} não encerrado nesta varredura: {}", pedidoId, excecao.getMessage());
            }
        }
    }

    private void cancelarPedidosSemPagamento() {
        List<Long> pedidos = pedidoRepository.buscarIdsSemPagamentoCriadosAntesDe(
                StatusPedido.AGUARDANDO_PAGAMENTO, LocalDateTime.now().minusMinutes(reservaMinutos));
        for (Long pedidoId : pedidos) {
            try {
                conciliacaoService.cancelarPedidoSemPagamento(pedidoId);
            } catch (RuntimeException excecao) {
                log.warn("Pedido {} não cancelado nesta varredura: {}", pedidoId, excecao.getMessage());
            }
        }
    }
}
