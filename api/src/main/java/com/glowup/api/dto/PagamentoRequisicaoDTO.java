package com.glowup.api.dto;

import com.glowup.api.domain.MetodoPagamento;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record PagamentoRequisicaoDTO(
    @NotNull(message = "O identificador do pedido é obrigatório")
    Long pedidoId,

    @NotNull(message = "O método de pagamento é obrigatório (PIX ou CARTAO)")
    MetodoPagamento metodo,

    @Min(value = 1, message = "O número de parcelas deve ser no mínimo 1")
    @Max(value = 3, message = "O parcelamento máximo permitido é de 3x")
    Integer parcelas,

    String tokenCartao,

    String bandeiraCartao,

    @Pattern(regexp = "^\\d{11}$", message = "O CPF do pagador deve conter 11 dígitos numéricos")
    String documentoPagador
) {

    // O CPF e o token do cartão são repassados ao gateway e nunca devem aparecer em logs (RD01).
    @Override
    public String toString() {
        return "PagamentoRequisicaoDTO[pedidoId=" + pedidoId + ", metodo=" + metodo + ", parcelas=" + parcelas + "]";
    }
}
