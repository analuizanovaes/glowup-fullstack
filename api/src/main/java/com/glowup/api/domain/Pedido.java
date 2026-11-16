package com.glowup.api.domain;

import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.glowup.api.model.Usuario;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;

@Getter
@Setter
@Entity
@Table(name = "pedidos", uniqueConstraints = @UniqueConstraint(
        name = "uk_pedido_cliente_idempotency", columnNames = {"usuario_id", "idempotency_key"}))
@NoArgsConstructor
@AllArgsConstructor
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @NotFound(action = NotFoundAction.IGNORE)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario cliente;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "desconto", nullable = false, precision = 12, scale = 2)
    private BigDecimal descontoCupom;

    @Column(name = "frete", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorFrete;

    @Column(name = "total", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorTotal;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private StatusPedido status;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL)
    private java.util.List<ItemPedido> itens;

    @Column(length = 10)
    private String cepEntrega;

    @Column(length = 100)
    private String bairroEntrega;

    /** true quando o ViaCEP estava fora do ar no checkout: bairro/CEP são os digitados e precisam de conferência. */
    @Column(name = "conferencia_endereco_pendente", nullable = false)
    private boolean conferenciaEnderecoPendente;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_entrega", nullable = false, length = 20)
    private TipoEntrega tipoEntrega;

    /** RN03: prazo unificado (o maior entre os itens) calculado no checkout. */
    @Column(name = "prazo_dias")
    private Integer prazoDias;

    @ManyToOne
    @JoinColumn(name = "cupom_id")
    private Cupom cupom;

    @Column(name = "data_pedido", nullable = false, updatable = false)
    private LocalDateTime dataCriacao;

    @PrePersist
    protected void onCreate() {
        this.dataCriacao = LocalDateTime.now();

        if (this.status == null) {
            this.status = StatusPedido.AGUARDANDO_PAGAMENTO;
        }
        if (this.descontoCupom == null) {
            this.descontoCupom = BigDecimal.ZERO;
        }
        if (this.valorFrete == null) {
            this.valorFrete = BigDecimal.ZERO;
        }
        if (this.tipoEntrega == null) {
            this.tipoEntrega = TipoEntrega.DOMICILIO;
        }
    }
}
