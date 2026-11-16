package com.glowup.api.dto;

public record NotificacaoWebhookDTO(
    String tipo,
    String acao,
    String dataId,
    String eventoId,
    String payload
) {}
