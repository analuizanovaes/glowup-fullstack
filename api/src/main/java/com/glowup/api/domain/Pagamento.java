package com.glowup.api.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "pagamentos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pagamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id", nullable = false, unique = true)
    private Pedido pedido;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MetodoPagamento metodo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private StatusPagamento status;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal valor;

    @Column(nullable = false)
    @Builder.Default
    private Integer parcelas = 1;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal juros = BigDecimal.ZERO;

    /** Nulo enquanto o gateway não informa o valor líquido; a taxa é então estimada (RN05). */
    @Column(name = "taxa", precision = 12, scale = 2)
    private BigDecimal taxaGateway;

    @Column(precision = 12, scale = 2)
    private BigDecimal valorLiquido;

    @Column(name = "identificador_gateway", unique = true, length = 150)
    private String idGateway;

    @Column(length = 100)
    private String statusDetalheGateway;

    @Column(name = "pix_copia_e_cola", columnDefinition = "TEXT")
    private String pixCopiaECola;

    /** Imagem do QR Code devolvida pelo Mercado Pago; persistida para sobreviver a retries e recarregamentos. */
    @Column(name = "pix_qr_code_base64", columnDefinition = "TEXT")
    private String pixQrCodeBase64;

    @Column(length = 500)
    private String pixLinkTicket;

    private LocalDateTime pixExpiraEm;

    private LocalDateTime dataPagamento;

    @CreationTimestamp
    @Column(name = "criado_at", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @UpdateTimestamp
    @Column(name = "atualizado_at", nullable = false)
    private LocalDateTime atualizadoEm;
}
