package com.glowup.api.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Controle físico: existe somente para produtos de Pronta-Entrega (RF02 / RNF02). */
@Entity
@Table(name = "estoques")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Estoque {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false, unique = true, updatable = false)
    private Produto produto;

    @Column(nullable = false)
    private int quantidade = 0;

    @Column(nullable = false)
    private int quantidadeReservada = 0;

    @Column(nullable = false)
    private int quantidadeMinima = 0;

    private LocalDate dataValidade;

    @UpdateTimestamp
    @Column(name = "atualizado_at", nullable = false)
    private LocalDateTime atualizadoEm;
}
