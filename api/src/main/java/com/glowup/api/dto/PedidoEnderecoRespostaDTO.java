package com.glowup.api.dto;

import com.glowup.api.domain.PedidoEndereco;
import com.glowup.api.domain.TipoEntrega;

public record PedidoEnderecoRespostaDTO(String pais, String cep, String logradouro, String numero,
        String complemento, String bairro, String cidade, String uf, TipoEntrega tipoEntrega, Integer prazoDias) {
    public static PedidoEnderecoRespostaDTO de(PedidoEndereco e) {
        return new PedidoEnderecoRespostaDTO(e.getPais(), e.getCep(), e.getLogradouro(), e.getNumero(),
                e.getComplemento(), e.getBairro(), e.getCidade(), e.getUf(), e.getTipoEntrega(), e.getPrazoDias());
    }
}
