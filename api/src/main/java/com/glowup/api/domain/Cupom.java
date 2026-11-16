package com.glowup.api.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cupons")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Cupom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String codigo;

    private BigDecimal percentualDesconto;

    private BigDecimal valorDesconto;

    private boolean ativo;

    /** Subtotal mínimo do pedido para o cupom valer (coluna valor_minimo, padrão 0). */
    @Column(name = "valor_minimo", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorMinimo = BigDecimal.ZERO;

    /** Limite total de usos; nulo = ilimitado. */
    @Column(name = "limite_uso")
    private Integer limiteUso;

    @Column(name = "usos_realizados", nullable = false)
    private Integer usosRealizados = 0;

    @Column(name = "data_inicio", nullable = false)
    private LocalDateTime validadeInicio;

    @Column(name = "data_fim", nullable = false)
    private LocalDateTime validadeFim;
}