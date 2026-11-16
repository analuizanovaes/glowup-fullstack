package com.glowup.api.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Alerta de proximidade de vencimento do estoque a Pronta-Entrega, visível apenas no painel da administradora. */
@Entity
@Table(name = "alertas_validade")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertaValidade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "estoque_id", nullable = false)
    private Estoque estoque;

    @Column(name = "data_prevista", nullable = false)
    private LocalDate dataPrevista;

    @Column(name = "dias_restantes", nullable = false)
    private Integer diasRestantes;

    @Column(columnDefinition = "TEXT")
    private String mensagem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StatusAlertaValidade status = StatusAlertaValidade.ABERTO;

    @CreationTimestamp
    @Column(name = "criado_at", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "resolvido_at")
    private LocalDateTime resolvidoEm;
}
