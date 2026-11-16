package com.glowup.api.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Apuração financeira congelada no momento da aprovação do pagamento (RN05).
 * Mudanças posteriores de preço ou custo no catálogo não alteram registros existentes.
 */
@Entity
@Table(name = "registros_financeiros")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegistroFinanceiro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id", nullable = false, unique = true)
    private Pedido pedido;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal valorVenda;

    @Column(precision = 12, scale = 2)
    private BigDecimal custoFabrica;

    @Column(name = "taxas_financeiras", precision = 12, scale = 2)
    private BigDecimal taxaGateway;

    @Column(precision = 12, scale = 2)
    private BigDecimal juros;

    @Column(name = "lucro", precision = 12, scale = 2)
    private BigDecimal lucroReal;

    @Column(nullable = false)
    private boolean dadosSuficientes;

    @Column(nullable = false)
    private boolean estornado;

    /** true quando o gateway não informou a taxa real e ela foi estimada pelo percentual configurado (RN05). */
    @Column(name = "taxa_estimada", nullable = false)
    private boolean taxaEstimada;

    @Column(name = "criado_at", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @PrePersist
    protected void aoCriar() {
        if (this.criadoEm == null) {
            this.criadoEm = LocalDateTime.now();
        }
    }
}
