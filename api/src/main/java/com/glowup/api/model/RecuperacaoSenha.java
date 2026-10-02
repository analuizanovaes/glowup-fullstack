package com.glowup.api.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "recuperacoes_senha")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecuperacaoSenha {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 6)
    private String codigoOtp;

    @Column(nullable = false)
    private LocalDateTime expiracao;

    @Column(nullable = false)
    @Builder.Default
    private boolean utilizado = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    public boolean isValido() {
        return !this.utilizado && LocalDateTime.now().isBefore(this.expiracao);
    }
}