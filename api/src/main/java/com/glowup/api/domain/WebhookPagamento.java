package com.glowup.api.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Evento recebido do gateway; o identificador único garante o descarte de notificações repetidas (RN04). */
@Entity
@Table(name = "webhooks_pagamento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookPagamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pagamento_id", nullable = false)
    private Pagamento pagamento;

    @Column(name = "identificador_gateway", nullable = false, unique = true, length = 150)
    private String eventoId;

    @Column(nullable = false, length = 80)
    private String tipoEvento;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false)
    private boolean processado;

    @Column(name = "recebido_at", nullable = false)
    private LocalDateTime recebidoEm;

    @Column(name = "processado_at")
    private LocalDateTime processadoEm;
}
