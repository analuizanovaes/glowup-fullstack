package com.glowup.api.service;

import com.glowup.api.exception.GatewayIndisponivelException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;

class MercadoPagoGatewaySandboxTest {
    // Perfil "prod" desliga o modo simulado: sem credencial, o gateway deve recusar tudo sem tocar na rede.
    private MercadoPagoGateway gateway() {
        MockEnvironment ambiente = new MockEnvironment();
        ambiente.setActiveProfiles("prod");
        return new MercadoPagoGateway("", "https://api.mercadopago.com", 100L, 100L, false, ambiente);
    }

    @Test void semCredencialBloqueiaCriacaoConsultaCancelamentoEEstornoSemRede() {
        MercadoPagoGateway gateway = gateway();
        assertThrows(GatewayIndisponivelException.class, () -> gateway.criarPagamento(Map.of(), "key"));
        assertThrows(GatewayIndisponivelException.class, () -> gateway.consultarPagamento("1"));
        assertThrows(GatewayIndisponivelException.class, () -> gateway.cancelarPagamento("1"));
        assertThrows(GatewayIndisponivelException.class, () -> gateway.estornarPagamento("1", "key", BigDecimal.ONE));
    }
}