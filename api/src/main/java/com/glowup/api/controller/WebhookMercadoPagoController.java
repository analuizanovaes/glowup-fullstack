package com.glowup.api.controller;

import com.glowup.api.dto.NotificacaoWebhookDTO;
import com.glowup.api.service.AssinaturaWebhookValidador;
import com.glowup.api.service.WebhookPagamentoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
@Tag(name = "Webhooks", description = "Receptor de notificações do Mercado Pago")
public class WebhookMercadoPagoController {

    private final WebhookPagamentoService webhookService;
    private final AssinaturaWebhookValidador assinaturaValidador;
    private final JsonMapper jsonMapper;

    @PostMapping("/mercadopago")
    @Operation(summary = "Recebe notificações de pagamento (idempotente)")
    public ResponseEntity<Void> receberNotificacao(
            @RequestBody(required = false) Map<String, Object> corpo,
            @RequestParam(name = "data.id", required = false) String dataIdNaUrl,
            @RequestParam(name = "type", required = false) String tipoNaUrl,
            @RequestHeader(name = "x-signature", required = false) String assinatura,
            @RequestHeader(name = "x-request-id", required = false) String idRequisicao) {

        Map<String, Object> dados = corpo != null ? corpo : Collections.emptyMap();
        String dataId = dataIdNaUrl != null ? dataIdNaUrl : extrairDataId(dados);

        if (!assinaturaValidador.validar(assinatura, dataId, idRequisicao)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        webhookService.processar(new NotificacaoWebhookDTO(
                tipoNaUrl != null ? tipoNaUrl : texto(dados.get("type")),
                texto(dados.get("action")),
                dataId,
                definirEventoId(dados, dataId,
                        tipoNaUrl != null ? tipoNaUrl : texto(dados.get("type")),
                        texto(dados.get("action")), idRequisicao, timestamp(assinatura)),
                serializar(dados)));
        return ResponseEntity.ok().build();
    }

    private String extrairDataId(Map<String, Object> dados) {
        if (dados.get("data") instanceof Map<?, ?> dadosInternos) {
            return texto(dadosInternos.get("id"));
        }
        return null;
    }

    private String definirEventoId(Map<String, Object> dados, String dataId, String tipo, String acao,
                                   String idRequisicao, String timestamp) {
        String idEvento = texto(dados.get("id"));
        if (idEvento != null) return idEvento;
        String entrada = String.join("|", tipo == null ? "" : tipo, dataId == null ? "" : dataId,
                acao == null ? "" : acao, idRequisicao == null ? "" : idRequisicao,
                timestamp == null ? "" : timestamp, serializar(dados));
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(entrada.getBytes(StandardCharsets.UTF_8));
            return "sem-id-" + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException erro) {
            throw new IllegalStateException("SHA-256 não está disponível para identificar o webhook.", erro);
        }
    }

    private String timestamp(String assinatura) {
        if (assinatura == null) return null;
        for (String parte : assinatura.split(",")) {
            String item = parte.trim();
            if (item.startsWith("ts=")) return item.substring(3);
        }
        return null;
    }

    private String serializar(Map<String, Object> dados) {
        try {
            return jsonMapper.writeValueAsString(dados);
        } catch (JacksonException erro) {
            throw new IllegalArgumentException("Não foi possível registrar o payload do webhook.", erro);
        }
    }

    private String texto(Object valor) {
        return valor == null ? null : String.valueOf(valor);
    }
}
