package com.glowup.api.service;

import com.glowup.api.domain.ItemPedido;
import com.glowup.api.domain.Produto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LogisticaServiceTest {

    private final LogisticaService logisticaService = new LogisticaService();

    @Test
    @DisplayName("Cobra frete fixo de R$ 10,00 em Itapoã e Paranoá, sem diferenciar maiúsculas")
    void deveCobrarFreteFixoNasRegioesAtendidas() {
        assertEquals(new BigDecimal("10.00"), logisticaService.calcularFrete("BR", "DF", "Itapoã"));
        assertEquals(new BigDecimal("10.00"), logisticaService.calcularFrete("br", "df", "paranoá"));
    }

    @Test
    @DisplayName("Bloqueia envio para fora do Brasil ou de outra UF (bloqueio em cascata)")
    void deveBloquearForaDoDistritoFederal() {
        IllegalArgumentException outroPais = assertThrows(IllegalArgumentException.class,
                () -> logisticaService.calcularFrete("US", "DF", "Itapoã"));
        IllegalArgumentException outraUf = assertThrows(IllegalArgumentException.class,
                () -> logisticaService.calcularFrete("BR", "SP", "Itapoã"));

        assertEquals("Entregas limitadas ao Distrito Federal, Brasil.", outroPais.getMessage());
        assertEquals("Entregas limitadas ao Distrito Federal, Brasil.", outraUf.getMessage());
    }

    @Test
    @DisplayName("Paranoá Parque e Itapoã Parque só aceitam retirada")
    void deveBloquearBairrosParque() {
        IllegalArgumentException paranoaParque = assertThrows(IllegalArgumentException.class,
                () -> logisticaService.calcularFrete("BR", "DF", "Paranoá Parque"));
        IllegalArgumentException itapoaParque = assertThrows(IllegalArgumentException.class,
                () -> logisticaService.calcularFrete("BR", "DF", "Itapoã Parque"));

        assertEquals("Frete domiciliar indisponível para esta localidade. Selecione 'Retirada Grátis'.", paranoaParque.getMessage());
        assertEquals("Frete domiciliar indisponível para esta localidade. Selecione 'Retirada Grátis'.", itapoaParque.getMessage());
    }

    @Test
    @DisplayName("Rejeita qualquer outra região do DF, inclusive bairro vazio")
    void deveRejeitarRegiaoNaoAtendida() {
        IllegalArgumentException outroBairro = assertThrows(IllegalArgumentException.class,
                () -> logisticaService.calcularFrete("BR", "DF", "Plano Piloto"));
        IllegalArgumentException semBairro = assertThrows(IllegalArgumentException.class,
                () -> logisticaService.calcularFrete("BR", "DF", null));

        assertEquals("Região não atendida pelo frete fixo atual.", outroBairro.getMessage());
        assertEquals("Região não atendida pelo frete fixo atual.", semBairro.getMessage());
    }

    @Test
    @DisplayName("Carrinho misto: o prazo unificado é o maior prazo entre os itens")
    void deveUnificarPrazoDeEntrega() {
        ItemPedido item = new ItemPedido();
        item.setProduto(new Produto());

        assertEquals(7, logisticaService.calcularPrazoUnificado(List.of(item, item)));
        assertEquals(0, logisticaService.calcularPrazoUnificado(List.of()));
        assertEquals(0, logisticaService.calcularPrazoUnificado(null));
    }
}
