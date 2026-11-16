package com.glowup.api.domain;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Kit = Produto com tipo_kit preenchido (DC02). FABRICA tem estoque próprio (herdado de Produto);
 * MANUAL baixa o estoque dos componentes avulsos.
 */
@Entity
@DiscriminatorValue("K")
public class Kit extends Produto {

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_kit", length = 20)
    private TipoKit tipo;

    @OneToMany(mappedBy = "kit", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemKit> itens = new ArrayList<>();

    public TipoKit getTipo() { return tipo; }
    public void setTipo(TipoKit tipo) { this.tipo = tipo; }

    public List<ItemKit> getItens() { return itens; }
    public void setItens(List<ItemKit> itens) { this.itens = itens; }
}
