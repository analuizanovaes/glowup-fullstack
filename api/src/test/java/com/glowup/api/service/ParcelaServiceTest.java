package com.glowup.api.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ParcelaServiceTest {

    private BigDecimal somar(List<BigDecimal> valores) {
        BigDecimal soma = BigDecimal.ZERO;
        for (BigDecimal valor : valores) {
            soma = soma.add(valor);
        }
        return soma;
    }

    @Test
    @DisplayName("Divide com centavos: a última parcela absorve a diferença")
    void deveFecharExatamenteNoTotal() {
        List<BigDecimal> valores = ParcelaService.calcularValoresDasParcelas(new BigDecimal("100.00"), 3);

        assertEquals(List.of(new BigDecimal("33.33"), new BigDecimal("33.33"), new BigDecimal("33.34")), valores);
        assertEquals(0, new BigDecimal("100.00").compareTo(somar(valores)));
    }

    @Test
    @DisplayName("Valor com juros divide igualmente quando é exato")
    void deveDividirIgualmenteQuandoExato() {
        List<BigDecimal> valores = ParcelaService.calcularValoresDasParcelas(new BigDecimal("103.50"), 3);

        assertEquals(List.of(new BigDecimal("34.50"), new BigDecimal("34.50"), new BigDecimal("34.50")), valores);
    }

    @Test
    @DisplayName("Pagamento à vista gera uma única parcela com o valor total")
    void deveGerarParcelaUnicaNaVista() {
        assertEquals(List.of(new BigDecimal("100.00")),
                ParcelaService.calcularValoresDasParcelas(new BigDecimal("100.00"), 1));
    }
}
