package com.glowup.api.service;

import com.glowup.api.domain.ItemPedido;
import com.glowup.api.domain.Pagamento;
import com.glowup.api.domain.Pedido;
import com.glowup.api.repository.PagamentoRepository;
import com.glowup.api.repository.PedidoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificacaoPedidoService {

    private static final Locale LOCALE_BRASIL = Locale.of("pt", "BR");

    private final PedidoRepository pedidoRepository;
    private final PagamentoRepository pagamentoRepository;
    private final EmailService emailService;

    @Value("${glowup.notificacoes.email-admin:}")
    private String emailAdministradora;

    @Value("${ADMIN_EMAIL:}")
    private String emailAdministradoraFallback;

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void notificarVendaAprovada(Long pedidoId) {
        Pedido pedido = pedidoRepository.findById(pedidoId).orElse(null);
        if (pedido == null) {
            return;
        }
        Pagamento pagamento = pagamentoRepository.findByPedidoId(pedidoId).orElse(null);

        if (pedido.getCliente() != null) {
            enviar(pedido.getCliente().getEmail(),
                    "GlowUp - Recibo do pedido #" + pedido.getId(),
                    montarRecibo(pedido, pagamento));
        }
        enviar(obterDestinoAdministradora(),
                "GlowUp - Nova venda: pedido #" + pedido.getId(),
                montarAlertaDeVenda(pedido));
    }

    private String obterDestinoAdministradora() {
        if (emailAdministradora != null && !emailAdministradora.isBlank()) return emailAdministradora;
        if (emailAdministradoraFallback != null && !emailAdministradoraFallback.isBlank()) return emailAdministradoraFallback;
        return null; // sem destino configurado: enviar() registra o aviso e não dispara e-mail
    }

    private void enviar(String destinatario, String assunto, String corpo) {
        if (destinatario == null || destinatario.isBlank()) {
            log.warn("Destinatário ausente para o e-mail '{}'.", assunto);
            return;
        }
        emailService.enviarEmailTexto(destinatario, assunto, corpo);
    }

    private String montarRecibo(Pedido pedido, Pagamento pagamento) {
        StringBuilder texto = new StringBuilder();
        texto.append("Olá, ").append(pedido.getCliente() == null ? "cliente" : pedido.getCliente().getNome()).append("!\n\n");
        texto.append("Recebemos o pagamento do seu pedido #").append(pedido.getId()).append(".\n\n");
        texto.append(descreverItens(pedido));
        texto.append("\nTotal do pedido: ").append(formatarValor(pedido.getValorTotal())).append('\n');
        if (pagamento != null) {
            texto.append("Forma de pagamento: ").append(pagamento.getMetodo());
            texto.append(pagamento.getParcelas() != null && pagamento.getParcelas() > 1
                    ? " em " + pagamento.getParcelas() + "x" : "").append('\n');
        }
        texto.append("\nObrigada por comprar na GlowUp!");
        return texto.toString();
    }

    private String montarAlertaDeVenda(Pedido pedido) {
        StringBuilder texto = new StringBuilder();
        texto.append("Nova venda confirmada.\n\n");
        texto.append("Pedido: #").append(pedido.getId()).append('\n');
        texto.append("Cliente: ").append(pedido.getCliente() == null ? "Conta removida" : pedido.getCliente().getNome()).append('\n');
        texto.append("Bairro de entrega: ").append(pedido.getBairroEntrega()).append("\n\n");
        texto.append(descreverItens(pedido));
        texto.append("\nTotal: ").append(formatarValor(pedido.getValorTotal()));
        return texto.toString();
    }

    private String descreverItens(Pedido pedido) {
        StringBuilder itens = new StringBuilder("Itens:\n");
        if (pedido.getItens() == null) {
            return itens.toString();
        }
        for (ItemPedido item : pedido.getItens()) {
            String nome = item.getProduto() != null ? item.getProduto().getNome() : "Produto";
            itens.append("- ").append(item.getQuantidade()).append("x ").append(nome)
                    .append(" (").append(formatarValor(item.getPrecoUnitario())).append(")\n");
        }
        return itens.toString();
    }

    private String formatarValor(BigDecimal valor) {
        return valor == null ? "-" : String.format(LOCALE_BRASIL, "R$ %,.2f", valor);
    }
}
