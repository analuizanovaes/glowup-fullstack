package com.glowup.api.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** Trilha de auditoria do estoque físico (DC05): toda entrada, saída, reintegração ou ajuste é registrada. */
@Entity
@Table(name = "movimentacoes_estoque")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovimentacaoEstoque {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "estoque_id", nullable = false)
    private Estoque estoque;

    @Column(name = "pedido_id")
    private Long pedidoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private TipoMovimentacao tipo;

    @Column(nullable = false)
    private Integer quantidade;

    @CreationTimestamp
    @Column(name = "data_movimentacao", nullable = false, updatable = false)
    private LocalDateTime dataMovimentacao;

    @Column(columnDefinition = "TEXT")
    private String observacao;
}
