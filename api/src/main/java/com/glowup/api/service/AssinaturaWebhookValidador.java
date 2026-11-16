package com.glowup.api.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

/**
 * Valida o cabeçalho x-signature enviado pelo Mercado Pago (HMAC-SHA256 sobre o manifesto
 * "id:[data.id];request-id:[x-request-id];ts:[ts];").
 */
@Service
@Slf4j
public class AssinaturaWebhookValidador {

    private static final String ALGORITMO = "HmacSHA256";

    @Value("${mercadopago.webhook-secret:}")
    private String webhookSecret;

    @Value("${mercadopago.webhook-tolerancia-segundos:900}")
    private long toleranciaTimestampSegundos = 900;

    public boolean validar(String cabecalhoAssinatura, String dataId, String idRequisicao) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.error("ERRO CRÍTICO: Segredo do Webhook não configurado no servidor.");
            return false; // Retorna false de forma segura em vez de estourar 500
        }

        // Correção de segurança: Se faltar o cabeçalho ou o ID, retorna false (gerando HTTP 401 no Controller)
        if (cabecalhoAssinatura == null || cabecalhoAssinatura.isBlank() || dataId == null || dataId.isBlank()) {
            return false;
        }

        Map<String, String> partes = extrairPartes(cabecalhoAssinatura);
        String timestamp = partes.get("ts");
        String assinaturaRecebida = partes.get("v1");
        
        if (timestamp == null || assinaturaRecebida == null) {
            return false;
        }
        if (!timestampAtual(timestamp)) {
            return false;
        }

        String assinaturaEsperada = calcularAssinatura(montarManifesto(dataId, idRequisicao, timestamp));
        return MessageDigest.isEqual(
                assinaturaEsperada.getBytes(StandardCharsets.UTF_8),
                assinaturaRecebida.getBytes(StandardCharsets.UTF_8));
    }

    private boolean timestampAtual(String timestamp) {
        try {
            long instanteAssinatura = Long.parseLong(timestamp);
            // O Mercado Pago pode enviar o ts em milissegundos; normaliza para segundos.
            if (instanteAssinatura > 100_000_000_000L) {
                instanteAssinatura = instanteAssinatura / 1000;
            }
            long agora = Instant.now().getEpochSecond();
            return instanteAssinatura <= agora + 60
                    && agora - instanteAssinatura <= toleranciaTimestampSegundos;
        } catch (NumberFormatException invalido) {
            return false;
        }
    }

    private Map<String, String> extrairPartes(String cabecalho) {
        Map<String, String> partes = new HashMap<>();
        for (String trecho : cabecalho.split(",")) {
            String[] chaveValor = trecho.trim().split("=", 2);
            if (chaveValor.length == 2) {
                partes.put(chaveValor[0].trim(), chaveValor[1].trim());
            }
        }
        return partes;
    }

    private String montarManifesto(String dataId, String idRequisicao, String timestamp) {
        StringBuilder manifesto = new StringBuilder("id:").append(dataId.toLowerCase(Locale.ROOT)).append(';');
        if (idRequisicao != null && !idRequisicao.isBlank()) {
            manifesto.append("request-id:").append(idRequisicao).append(';');
        }
        return manifesto.append("ts:").append(timestamp).append(';').toString();
    }

    private String calcularAssinatura(String manifesto) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), ALGORITMO));
            return HexFormat.of().formatHex(mac.doFinal(manifesto.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException excecao) {
            throw new IllegalStateException("Falha ao calcular a assinatura do webhook.", excecao);
        }
    }
}
