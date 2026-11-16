package com.glowup.api.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** Notificação interna do painel da administradora (nova venda, validade, reposição). */
@Entity
@Table(name = "notificacoes_admin")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificacaoAdmin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String tipo;

    @Column(length = 150)
    private String titulo;

    @Column(columnDefinition = "TEXT")
    private String mensagem;

    @Column(name = "pedido_id")
    private Long pedidoId;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(nullable = false)
    @Builder.Default
    private boolean visualizada = false;

    @CreationTimestamp
    @Column(name = "criada_at", nullable = false, updatable = false)
    private LocalDateTime criadaEm;
}
