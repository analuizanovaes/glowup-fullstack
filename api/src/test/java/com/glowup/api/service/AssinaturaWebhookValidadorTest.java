package com.glowup.api.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class AssinaturaWebhookValidadorTest {
    private static final String SECRET = "segredo-teste";
    private AssinaturaWebhookValidador validador;

    @BeforeEach void preparar() {
        validador = new AssinaturaWebhookValidador();
        ReflectionTestUtils.setField(validador, "webhookSecret", SECRET);
    }

    @Test void aceitaAssinaturaAtualValida() {
        String ts = String.valueOf(Instant.now().getEpochSecond());
        assertTrue(validador.validar(assinar("123", "req-1", ts), "123", "req-1"));
    }

    @Test void rejeitaAssinaturaAlteradaEAntiga() {
        String ts = String.valueOf(Instant.now().getEpochSecond());
        assertFalse(validador.validar(assinar("123", "req-1", ts), "124", "req-1"));
        String antigo = String.valueOf(Instant.now().minusSeconds(901).getEpochSecond());
        assertFalse(validador.validar(assinar("123", "req-1", antigo), "123", "req-1"));
        assertFalse(validador.validar(null, "123", "req-1"));
    }

    private String assinar(String id, String requestId, String ts) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String manifesto = "id:" + id + ";request-id:" + requestId + ";ts:" + ts + ";";
            return "ts=" + ts + ",v1=" + HexFormat.of().formatHex(mac.doFinal(manifesto.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
