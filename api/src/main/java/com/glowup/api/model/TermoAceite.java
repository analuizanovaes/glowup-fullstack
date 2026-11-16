package com.glowup.api.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Registro imutável de aceite dos Termos de Uso, com data, hora exata e versão (RD04). */
@Entity
@Table(name = "termos_aceites")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TermoAceite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50, updatable = false)
    private String tipoAceite;

    @Column(name = "versao_termos", nullable = false, length = 20, updatable = false)
    private String versaoDocumento;

    @Column(name = "data_aceite_termos", nullable = false, updatable = false)
    private LocalDateTime dataHoraAceite;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false, updatable = false)
    private Usuario usuario;
}
