package com.glowup.api.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** Código OTP de recuperação de senha. Apenas o hash do código é persistido. */
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

    @Column(nullable = false)
    private String codigoOtpHash;

    @Column(nullable = false)
    private LocalDateTime expiracao;

    @Column(nullable = false)
    @Builder.Default
    private boolean utilizado = false;

    @Column(nullable = false)
    @Builder.Default
    private int tentativas = 0;

    @CreationTimestamp
    @Column(name = "criado_at", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "utilizado_at")
    private LocalDateTime utilizadoEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    public boolean isValido() {
        return !this.utilizado && LocalDateTime.now().isBefore(this.expiracao);
    }
}
