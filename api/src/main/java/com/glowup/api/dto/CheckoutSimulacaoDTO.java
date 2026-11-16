package com.glowup.api.dto;

import com.glowup.api.domain.TipoEntrega;

/** Dados de entrega e cupom usados para prévia; a simulação nunca cria pedido nem consome estoque/cupom. */
public record CheckoutSimulacaoDTO(TipoEntrega tipoEntrega, Long enderecoId, String pais, String cep,
        String logradouro, String numero, String complemento, String bairro, String cidade, String uf,
        String codigoCupom) {
    public CheckoutRequisicaoDTO comoCheckout() {
        return new CheckoutRequisicaoDTO(null, null, tipoEntrega, enderecoId, pais, cep, logradouro,
                numero, complemento, bairro, cidade, uf, codigoCupom);
    }
}
