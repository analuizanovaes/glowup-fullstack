package com.glowup.api.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Snapshot imutável do endereço de entrega (ou da retirada) no momento do pedido (DC04 / RN03). */
@Entity
@Table(name = "pedido_enderecos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PedidoEndereco {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pedido_id", nullable = false, unique = true)
    private Long pedidoId;

    @Column(name = "endereco_id")
    private Long enderecoId;

    @Column(nullable = false, length = 60)
    @Builder.Default
    private String pais = "Brasil";

    @Column(length = 10)
    private String cep;

    @Column(length = 180)
    private String logradouro;

    @Column(length = 30)
    private String numero;

    @Column(length = 120)
    private String complemento;

    @Column(length = 100)
    private String bairro;

    @Column(length = 100)
    private String cidade;

    @Column(length = 2)
    private String uf;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_entrega", nullable = false, length = 20)
    private TipoEntrega tipoEntrega;

    @Column(name = "prazo_dias")
    private Integer prazoDias;
}
