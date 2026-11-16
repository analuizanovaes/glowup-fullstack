package com.glowup.api.dto;

import com.glowup.api.domain.Pedido;
import com.glowup.api.domain.PedidoEndereco;
import com.glowup.api.domain.StatusPedido;
import com.glowup.api.domain.TipoEntrega;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

public record PedidoClienteRespostaDTO(
    Long id,
    LocalDateTime dataCriacao,
    StatusPedido status,
    BigDecimal subtotal,
    BigDecimal descontoCupom,
    BigDecimal valorFrete,
    BigDecimal valorTotal,
    String bairroEntrega,
    TipoEntrega tipoEntrega,
    Integer prazoDias,
    LocalDateTime dataEntrega,
    LocalDateTime limiteSolicitacaoDevolucao,
    List<ItemPedidoClienteDTO> itens,
    String linkWhatsappRetirada,
    PedidoEnderecoRespostaDTO enderecoEntrega
) {

    /** Prazo padrão de devolução, usado pelos overloads que não recebem a configuração explicitamente. */
    private static final long PRAZO_DEVOLUCAO_PADRAO_DIAS = 7;

    public static PedidoClienteRespostaDTO de(Pedido pedido) {
        return de(pedido, null, null, PRAZO_DEVOLUCAO_PADRAO_DIAS);
    }

    /**
     * RN09: link wa.me com mensagem pré-formatada, somente em pedidos de Retirada. O número vem da configuração
     * (WHATSAPP_NUMERO) e só é entregue à cliente dona do pedido, nunca na vitrine pública.
     */
    public static PedidoClienteRespostaDTO de(Pedido pedido, String numeroWhatsapp) {
        return de(pedido, numeroWhatsapp, null, PRAZO_DEVOLUCAO_PADRAO_DIAS);
    }

    public static PedidoClienteRespostaDTO de(Pedido pedido, String numeroWhatsapp, LocalDateTime dataEntrega) {
        return de(pedido, numeroWhatsapp, dataEntrega, PRAZO_DEVOLUCAO_PADRAO_DIAS);
    }

    /**
     * @param prazoDevolucaoDias deve vir de glowup.devolucao.prazo-dias, o mesmo usado por DevolucaoService,
     *                           para que o limite exibido à cliente não divirja do limite validado no servidor.
     */
    public static PedidoClienteRespostaDTO de(
            Pedido pedido, String numeroWhatsapp, LocalDateTime dataEntrega, long prazoDevolucaoDias) {
        return de(pedido, numeroWhatsapp, dataEntrega, prazoDevolucaoDias, null);
    }

    public static PedidoClienteRespostaDTO de(
            Pedido pedido, String numeroWhatsapp, LocalDateTime dataEntrega, long prazoDevolucaoDias,
            PedidoEndereco endereco) {
        List<ItemPedidoClienteDTO> itens = pedido.getItens() == null
                ? List.of()
                : pedido.getItens().stream().map(ItemPedidoClienteDTO::de).toList();

        return new PedidoClienteRespostaDTO(
                pedido.getId(),
                pedido.getDataCriacao(),
                pedido.getStatus(),
                pedido.getSubtotal(),
                pedido.getDescontoCupom(),
                pedido.getValorFrete(),
                pedido.getValorTotal(),
                pedido.getBairroEntrega(),
                pedido.getTipoEntrega(),
                pedido.getPrazoDias(),
                dataEntrega,
                dataEntrega == null ? null : dataEntrega.plusDays(prazoDevolucaoDias),
                itens,
                montarLinkWhatsapp(pedido, numeroWhatsapp),
                endereco == null ? null : PedidoEnderecoRespostaDTO.de(endereco));
    }

    private static String montarLinkWhatsapp(Pedido pedido, String numeroWhatsapp) {
        String numero = numeroWhatsapp == null ? "" : numeroWhatsapp.replaceAll("\\D", "");
        if (numero.isEmpty() || pedido.getTipoEntrega() != TipoEntrega.RETIRADA) {
            return null;
        }
        String mensagem = "Olá! Fiz o pedido #" + pedido.getId() + " na GlowUp com retirada no local e gostaria de combinar o horário.";
        return "https://wa.me/" + numero + "?text=" + URLEncoder.encode(mensagem, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
