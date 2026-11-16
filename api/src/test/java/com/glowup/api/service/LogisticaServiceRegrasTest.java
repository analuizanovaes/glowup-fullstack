package com.glowup.api.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LogisticaServiceRegrasTest {

    private final LogisticaService logisticaService = new LogisticaService();

    @Test
    @DisplayName("Bairro sem acento e país 'Brasil' também liberam o frete fixo")
    void aceitaVariacoesDeEscrita() {
        assertEquals(new BigDecimal("10.00"), logisticaService.calcularFrete("Brasil", "df", "itapoa"));
        assertEquals(new BigDecimal("10.00"), logisticaService.calcularFrete("BR", "DF", "  PARANOÁ "));
    }

    @Test
    @DisplayName("Itapoã II, Del Lago, Del Lago II, Baixinho e condomínios Itapoã I/II também têm frete fixo")
    void aceitaBairrosECondominiosAtendidos() {
        for (String bairro : new String[]{"Itapoã II", "Del Lago", "Del Lago II", "Baixinho",
                "Condomínio Itapoã I", "Condomínio Itapoã II", "condominio itapoa ii", "Itapoã - II"}) {
            assertEquals(new BigDecimal("10.00"), logisticaService.calcularFrete("BR", "DF", bairro), bairro);
        }
    }

    @Test
    @DisplayName("Bairro não listado continua rejeitado")
    void bairroNaoListadoContinuaRejeitado() {
        assertThrows(IllegalArgumentException.class, () -> logisticaService.calcularFrete("BR", "DF", "Del Lago III"));
        assertThrows(IllegalArgumentException.class, () -> logisticaService.calcularFrete("BR", "DF", "Asa Norte"));
    }

    @Test
    @DisplayName("Bairros Parque continuam bloqueados mesmo sem acento")
    void parqueContinuaBloqueado() {
        assertThrows(IllegalArgumentException.class, () -> logisticaService.calcularFrete("BR", "DF", "Itapoa Parque"));
        assertThrows(IllegalArgumentException.class, () -> logisticaService.calcularFrete("BR", "DF", "Paranoá Parque"));
    }

    @Test
    @DisplayName("CEP com formato inválido é rejeitado antes de chamar o ViaCEP")
    void cepInvalidoEhRejeitado() {
        assertThrows(IllegalArgumentException.class, () -> logisticaService.consultarCep("123"));
    }

    @Test
    @DisplayName("Aceita Paranoá, Itapoã, Itapuã e Itapoá (qualquer grafia/caixa) e bloqueia as versões Parque")
    void grafiasAceitasEBloqueioParque() {
        for (String bairro : new String[]{"Paranoá", "paranoá", "Itapoã", "itapoã", "Itapuã", "itapuã", "Itapoá", "itapoá"}) {
            assertEquals(new BigDecimal("10.00"), logisticaService.calcularFrete("BR", "DF", bairro), bairro);
        }
        for (String bairro : new String[]{"Paranoá Parque", "paranoá parque", "Itapoã Parque", "itapoã parque",
                "Itapuã Parque", "itapuã parque", "Itapoá Parque", "itapoá parque"}) {
            assertThrows(IllegalArgumentException.class, () -> logisticaService.calcularFrete("BR", "DF", bairro), bairro);
        }
    }
}
