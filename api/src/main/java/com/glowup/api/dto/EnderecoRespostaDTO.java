package com.glowup.api.dto;

import com.glowup.api.model.Endereco;

public record EnderecoRespostaDTO(
    Long id,
    String pais,
    String cep,
    String logradouro,
    String numero,
    String complemento,
    String bairro,
    String cidade,
    String uf,
    boolean principal
) {
    public static EnderecoRespostaDTO de(Endereco e) {
        return new EnderecoRespostaDTO(e.getId(), e.getPais(), e.getCep(), e.getLogradouro(), e.getNumero(),
                e.getComplemento(), e.getBairro(), e.getCidade(), e.getUf(), e.isPrincipal());
    }
}
