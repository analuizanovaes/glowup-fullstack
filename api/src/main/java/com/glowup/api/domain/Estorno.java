package com.glowup.api.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "estornos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Estorno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "devolucao_id", nullable = false, unique = true)
    private Devolucao devolucao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pagamento_id", nullable = false)
    private Pagamento pagamento;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal valor;

    @Column(nullable = false, length = 255)
    private String motivo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatusEstorno status;

    @CreationTimestamp
    @Column(name = "criado_at", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "processado_at")
    private LocalDateTime processadoEm;
}
