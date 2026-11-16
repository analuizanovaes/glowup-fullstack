package com.glowup.api.dto;

import com.glowup.api.domain.Pedido;
import com.glowup.api.domain.StatusPedido;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import com.glowup.api.domain.PedidoEndereco;
import com.glowup.api.domain.Pagamento;

public record PedidoAdminRespostaDTO(
    Long id,
    String clienteNome,
    String clienteEmail,
    LocalDateTime dataCriacao,
    StatusPedido status,
    BigDecimal subtotal,
    BigDecimal descontoCupom,
    BigDecimal valorFrete,
    BigDecimal valorTotal,
    String bairroEntrega,
    String telefoneCliente,
    PedidoEnderecoRespostaDTO enderecoEntrega,
    PagamentoAdminResumoDTO pagamento,
    boolean conferirEndereco,
    List<ItemPedidoAdminDTO> itens,
    LucroRealDTO lucroReal
) {

    public static PedidoAdminRespostaDTO de(Pedido pedido, LucroRealDTO lucroReal) {
        return de(pedido, lucroReal, null, null);
    }

    public static PedidoAdminRespostaDTO de(Pedido pedido, LucroRealDTO lucroReal, PedidoEndereco endereco, Pagamento pagamento) {
        List<ItemPedidoAdminDTO> itens = pedido.getItens() == null
                ? List.of()
                : pedido.getItens().stream().map(ItemPedidoAdminDTO::de).toList();

        return new PedidoAdminRespostaDTO(
                pedido.getId(),
                pedido.getCliente() == null ? "Cliente removida" : pedido.getCliente().getNome(),
                pedido.getCliente() == null ? null : pedido.getCliente().getEmail(),
                pedido.getDataCriacao(),
                pedido.getStatus(),
                pedido.getSubtotal(),
                pedido.getDescontoCupom(),
                pedido.getValorFrete(),
                pedido.getValorTotal(),
                pedido.getBairroEntrega(),
                pedido.getCliente() == null ? null : pedido.getCliente().getTelefones().stream()
                        .filter(t -> t.isPrincipal()).map(t -> t.getNumero()).findFirst().orElse(null),
                endereco == null ? null : PedidoEnderecoRespostaDTO.de(endereco),
                pagamento == null ? null : PagamentoAdminResumoDTO.de(pagamento),
                pedido.isConferenciaEnderecoPendente(),
                itens,
                lucroReal);
    }
}
