package com.glowup.api.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;

/** Item e quantidade que a cliente pediu para devolver (tabela itens_devolucao). Base da devolução parcial. */
@Entity
@Table(name = "itens_devolucao")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItemDevolucao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "devolucao_id", nullable = false)
    private Devolucao devolucao;

    @ManyToOne(fetch = FetchType.LAZY)
    @NotFound(action = NotFoundAction.IGNORE)
    @JoinColumn(name = "produto_id", insertable = false, updatable = false)
    private Produto produto;

    /** Id gravado de forma independente: continua válido quando o produto é inativado depois da venda. */
    @Column(name = "produto_id", nullable = false)
    private Long produtoId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_pedido_id", nullable = false)
    private ItemPedido itemPedido;

    @Column(nullable = false)
    private Integer quantidade;

    /** Null = a administração ainda não avaliou este item. */
    @Column(name = "produto_lacrado")
    private Boolean produtoLacrado;
}
