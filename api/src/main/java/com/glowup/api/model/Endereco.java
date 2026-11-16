package com.glowup.api.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/** Endereço de entrega da cliente (RF01 / DC01). Exclusão sempre lógica: ativo = false + deleted_at. */
@Entity
@Table(name = "enderecos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLRestriction("ativo = true")
public class Endereco {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false, length = 60)
    @Builder.Default
    private String pais = "Brasil";

    @Column(nullable = false, length = 10)
    private String cep;

    @Column(nullable = false, length = 180)
    private String logradouro;

    @Column(nullable = false, length = 30)
    private String numero;

    @Column(length = 120)
    private String complemento;

    @Column(nullable = false, length = 100)
    private String bairro;

    @Column(nullable = false, length = 100)
    private String cidade;

    @Column(nullable = false, length = 2)
    private String uf;

    @Column(nullable = false)
    @Builder.Default
    private boolean principal = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean ativo = true;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;

    /** A tabela exige deleted_at preenchido sempre que ativo = false. */
    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
        this.deletedAt = ativo ? null : LocalDateTime.now();
    }
}
