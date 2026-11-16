package com.glowup.api.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** Sessão renovável. Só o hash SHA-256 do token é guardado; o valor original existe apenas no cliente. */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Sem associação JPA: Usuario tem filtro de exclusão lógica, e uma conta excluída não pode quebrar a consulta.
    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expira_em", nullable = false)
    private LocalDateTime expiraEm;

    @Column(nullable = false)
    @Builder.Default
    private boolean revogado = false;

    /** Permite responder a refresh duplicado em outra instância sem guardar o token original. */
    @Column(name = "rotacionado_em")
    private LocalDateTime rotacionadoEm;

    @Column(name = "token_substituto_hash", length = 64)
    private String tokenSubstitutoHash;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;
}
