package com.glowup.api.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;

@Entity
@Table(name = "kit_itens")
public class ItemKit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(optional = false)
    @JoinColumn(name = "produto_kit_id", nullable = false)
    private Kit kit;

    @ManyToOne(optional = false)
    @NotFound(action = NotFoundAction.IGNORE)
    @JoinColumn(name = "produto_componente_id", nullable = false)
    private Produto produto;

    @Column(name = "produto_componente_id", insertable = false, updatable = false)
    private Long produtoIdPersistido;

    @Column(nullable = false)
    private Integer quantidade = 1;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Kit getKit() { return kit; }
    public void setKit(Kit kit) { this.kit = kit; }

    public Produto getProduto() { return produto; }
    public void setProduto(Produto produto) { this.produto = produto; }

    /** Usa a coluna persistida quando disponível; em memória (ou antes do flush) cai para o próprio produto. */
    public Long getProdutoIdPersistido() {
        return produtoIdPersistido != null ? produtoIdPersistido : (produto != null ? produto.getId() : null);
    }

    public Integer getQuantidade() { return quantidade; }
    public void setQuantidade(Integer quantidade) { this.quantidade = quantidade; }
}
