package com.glowup.api.service;

import com.glowup.api.repository.PedidoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Depois do commit da aprovação do pagamento: conclui a sacola da cliente e cria a notificação de nova
 * venda no painel da administradora. Enquanto não há aprovação o carrinho permanece intacto (RN06).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PedidoPagoCarrinhoListener {

    private final PedidoRepository pedidoRepository;
    private final CarrinhoService carrinhoService;
    private final CrmAdminService crmAdminService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoAprovarPagamento(PagamentoAprovadoEvento evento) {
        try {
            pedidoRepository.findById(evento.pedidoId()).ifPresent(pedido -> {
                if (pedido.getCliente() != null) {
                    carrinhoService.concluirCarrinho(pedido.getCliente().getId());
                }
                crmAdminService.registrarNovaVenda(pedido.getId(), pedido.getCliente() == null ? "Conta removida" : pedido.getCliente().getNome(),
                        pedido.getValorTotal());
            });
        } catch (RuntimeException excecao) {
            log.error("Falha ao concluir o carrinho/notificar o painel do pedido {}: {}",
                    evento.pedidoId(), excecao.getMessage());
        }
    }
}
