package com.glowup.api.dto;

import java.math.BigDecimal;

/**
 * Resultado da consulta de CEP com as opções logísticas (RN03 / RNF01). Quando permiteDigitacaoManual = true
 * o front deve liberar os campos de endereço para digitação (contingência do ViaCEP, seção 6.18).
 */
public record ConsultaCepRespostaDTO(
    String cep,
    String logradouro,
    String bairro,
    String cidade,
    String uf,
    boolean atendeDomicilio,
    BigDecimal valorFrete,
    int prazoDias,
    boolean permiteDigitacaoManual,
    boolean servicoIndisponivel,
    String mensagem
) {}
