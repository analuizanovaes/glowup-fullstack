package com.glowup.api.domain;

import com.glowup.api.model.Usuario;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "devolucoes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Devolucao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @NotFound(action = NotFoundAction.IGNORE)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private StatusDevolucao status;

    @Column(nullable = false, length = 500)
    private String motivo;

    private Boolean produtoLacrado;

    @Column(nullable = false)
    @Builder.Default
    private boolean arrependimento = false;

    @Column(length = 500)
    private String observacaoAnalise;

    @Column(nullable = false)
    private LocalDateTime dataSolicitacao;

    private LocalDateTime dataAnalise;

    private LocalDateTime dataRecebimento;

    @Column(precision = 12, scale = 2)
    private BigDecimal valorEstornado;

    @Column(length = 50)
    private String idEstornoGateway;

    @OneToMany(mappedBy = "devolucao", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ItemDevolucao> itens = new ArrayList<>();
}
