package com.glowup.api.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/** Sacola persistida no servidor (RF06 / seção 6.23): sobrevive à troca de página e a falhas do gateway (RN06). */
@Entity
@Table(name = "carrinhos")
@Getter
@Setter
@NoArgsConstructor
public class Carrinho {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusCarrinho status = StatusCarrinho.ATIVO;

    @CreationTimestamp
    @Column(name = "criado_at", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @UpdateTimestamp
    @Column(name = "atualizado_at", nullable = false)
    private LocalDateTime atualizadoEm;
}
