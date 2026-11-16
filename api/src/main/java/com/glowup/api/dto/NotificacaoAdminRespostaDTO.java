package com.glowup.api.dto;

import com.glowup.api.domain.NotificacaoAdmin;

import java.time.LocalDateTime;

public record NotificacaoAdminRespostaDTO(
    Long id,
    String tipo,
    String titulo,
    String mensagem,
    Long pedidoId,
    boolean visualizada,
    LocalDateTime criadaEm
) {
    public static NotificacaoAdminRespostaDTO de(NotificacaoAdmin n) {
        return new NotificacaoAdminRespostaDTO(n.getId(), n.getTipo(), n.getTitulo(), n.getMensagem(),
                n.getPedidoId(), n.isVisualizada(), n.getCriadaEm());
    }
}
