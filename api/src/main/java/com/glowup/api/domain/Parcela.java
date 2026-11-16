package com.glowup.api.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "parcelas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Parcela {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pagamento_id", nullable = false)
    private Pagamento pagamento;

    @Column(nullable = false)
    private Integer numero;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal valor;

    private LocalDate vencimento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private StatusParcela status;

    private LocalDateTime pagoEm;
}
