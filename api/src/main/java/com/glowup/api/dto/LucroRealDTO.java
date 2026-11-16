package com.glowup.api.dto;

import java.math.BigDecimal;

public record LucroRealDTO(
    BigDecimal valorVenda,
    BigDecimal custoFabrica,
    BigDecimal taxasFinanceiras,
    BigDecimal lucroReal,
    String observacao,
    boolean taxaEstimada
) {

    public static final String DADOS_INSUFICIENTES = "Dados Insuficientes";

    public static LucroRealDTO indisponivel(String observacao) {
        
        return new LucroRealDTO(null, null, null, null, observacao, false); 
    }
}