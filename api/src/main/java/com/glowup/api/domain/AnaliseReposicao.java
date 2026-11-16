package com.glowup.api.domain;

import com.glowup.api.model.Usuario;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Sugestão do módulo CRM: momento ideal de contatar a cliente para reposição (DC07 / seção 6.15). */
@Entity
@Table(name = "analises_reposicao")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnaliseReposicao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(name = "pedido_id")
    private Long pedidoId;

    @Column(name = "data_compra")
    private LocalDate dataCompra;

    @Column(name = "tempo_medio_uso_dias")
    private Integer tempoMedioUsoDias;

    @Column(name = "data_estimada_reposicao")
    private LocalDate dataEstimadaReposicao;

    @Column(name = "quantidade_sugerida", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal quantidadeSugerida = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    @Builder.Default
    private StatusReposicao status = StatusReposicao.PENDENTE;

    @CreationTimestamp
    @Column(name = "criada_at", nullable = false, updatable = false)
    private LocalDateTime criadaEm;

    @UpdateTimestamp
    @Column(name = "atualizada_at", nullable = false)
    private LocalDateTime atualizadaEm;
}
