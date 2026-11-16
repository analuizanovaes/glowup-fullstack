package com.glowup.api.service;

import com.glowup.api.domain.Cupom;
import com.glowup.api.repository.CupomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CupomServiceTest {

    private CupomRepository cupomRepository;
    private CupomService cupomService;

    @BeforeEach
    void preparar() {
        cupomRepository = mock(CupomRepository.class);
        cupomService = new CupomService(cupomRepository);
    }

    private Cupom cupomPercentual(String percentual) {
        Cupom cupom = new Cupom();
        cupom.setId(1L);
        cupom.setCodigo("GLOW10");
        cupom.setPercentualDesconto(new BigDecimal(percentual));
        cupom.setAtivo(true);
        cupom.setValidadeInicio(LocalDateTime.now().minusDays(1));
        cupom.setValidadeFim(LocalDateTime.now().plusDays(1));
        return cupom;
    }

    @Test
    @DisplayName("Aplica desconto percentual sobre o subtotal e consome um uso")
    void deveAplicarDescontoPercentual() {
        when(cupomRepository.findByCodigoIgnoreCase("GLOW10")).thenReturn(Optional.of(cupomPercentual("10")));
        when(cupomRepository.registrarUso(1L)).thenReturn(1);

        CupomService.DescontoAplicado resultado = cupomService.validarEConsumir(" glow10 ", new BigDecimal("120.00"));

        assertEquals(new BigDecimal("12.00"), resultado.valor());
    }

    @Test
    @DisplayName("Simulação calcula desconto sem consumir o cupom")
    void deveSimularSemConsumirUso() {
        when(cupomRepository.findByCodigoIgnoreCase("GLOW10")).thenReturn(Optional.of(cupomPercentual("10")));

        CupomService.DescontoAplicado resultado = cupomService.simular("GLOW10", new BigDecimal("120.00"));

        assertEquals(new BigDecimal("12.00"), resultado.valor());
        verify(cupomRepository, never()).registrarUso(anyLong());
    }

    @Test
    @DisplayName("Desconto em valor fixo nunca ultrapassa o subtotal")
    void descontoFixoNaoUltrapassaSubtotal() {
        Cupom cupom = cupomPercentual("10");
        cupom.setPercentualDesconto(null);
        cupom.setValorDesconto(new BigDecimal("500.00"));
        when(cupomRepository.findByCodigoIgnoreCase("GLOW10")).thenReturn(Optional.of(cupom));
        when(cupomRepository.registrarUso(1L)).thenReturn(1);

        CupomService.DescontoAplicado resultado = cupomService.validarEConsumir("GLOW10", new BigDecimal("80.00"));

        assertEquals(new BigDecimal("80.00"), resultado.valor());
    }

    @Test
    @DisplayName("Cupom inexistente é rejeitado")
    void deveRejeitarCupomInexistente() {
        when(cupomRepository.findByCodigoIgnoreCase(anyString())).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> cupomService.validarEConsumir("NAOEXISTE", new BigDecimal("50.00")));
    }

    @Test
    @DisplayName("Cupom expirado ou inativo é rejeitado sem consumir uso")
    void deveRejeitarCupomExpirado() {
        Cupom cupom = cupomPercentual("10");
        cupom.setValidadeFim(LocalDateTime.now().minusMinutes(1));
        when(cupomRepository.findByCodigoIgnoreCase("GLOW10")).thenReturn(Optional.of(cupom));

        assertThrows(IllegalArgumentException.class,
                () -> cupomService.validarEConsumir("GLOW10", new BigDecimal("50.00")));
        verify(cupomRepository, never()).registrarUso(anyLong());
    }

    @Test
    @DisplayName("Respeita o valor mínimo do pedido")
    void deveRespeitarValorMinimo() {
        Cupom cupom = cupomPercentual("10");
        cupom.setValorMinimo(new BigDecimal("100.00"));
        when(cupomRepository.findByCodigoIgnoreCase("GLOW10")).thenReturn(Optional.of(cupom));

        assertThrows(IllegalArgumentException.class,
                () -> cupomService.validarEConsumir("GLOW10", new BigDecimal("99.99")));
        verify(cupomRepository, never()).registrarUso(any());
    }

    @Test
    @DisplayName("Limite de usos esgotado rejeita o cupom")
    void deveRejeitarLimiteDeUsosAtingido() {
        when(cupomRepository.findByCodigoIgnoreCase("GLOW10")).thenReturn(Optional.of(cupomPercentual("10")));
        when(cupomRepository.registrarUso(1L)).thenReturn(0);

        assertThrows(IllegalArgumentException.class,
                () -> cupomService.validarEConsumir("GLOW10", new BigDecimal("50.00")));
    }
}