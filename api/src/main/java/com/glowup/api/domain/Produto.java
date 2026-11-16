package com.glowup.api.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DiscriminatorFormula;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Produto do catálogo. Kits são produtos (linhas com tipo_kit preenchido) e por isso a classe Kit a especializa.
 * O saldo físico fica na tabela estoques; os acessores de estoque abaixo preservam a API usada pelos serviços.
 */
@Entity
@Table(name = "produtos")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorFormula("CASE WHEN tipo_kit IS NULL THEN 'P' ELSE 'K' END")
@DiscriminatorValue("P")
@SQLDelete(sql = "UPDATE produtos SET ativo = false, deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction(value = "ativo = true")
public class Produto {

    /** RN03 / seção 6.43: prazo padrão unificado de entrega. */
    private static final int PRAZO_ENTREGA_PADRAO_DIAS = 7;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 180)
    private String nome;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Column(nullable = false, length = 80)
    private String marca;

    @ManyToOne(optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal custoFabrica;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precoBase;

    @Column(precision = 12, scale = 2)
    private BigDecimal precoPromocional;

    private LocalDateTime dataInicioOferta;

    @Column(name = "data_fim_promocao")
    private LocalDateTime dataLimitePromocao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ModalidadeProduto modalidade;

    private Integer tempoMedioUsoDias;

    @Column(columnDefinition = "TEXT")
    private String imagemUrl;

    @Column(nullable = false)
    private boolean ativo = true;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @JsonIgnore
    private LocalDateTime deletedAt;

    @JsonIgnore
    @OneToOne(mappedBy = "produto", cascade = CascadeType.ALL, orphanRemoval = true)
    private Estoque estoque;

    @PrePersist
    @PreUpdate
    public void validarEstoque() {
        if (this.modalidade == ModalidadeProduto.PRONTA_ENTREGA && this.estoque == null) {
            throw new IllegalArgumentException("Quantidade física obrigatória para Pronta-Entrega.");
        }
        if (this.modalidade == ModalidadeProduto.SOB_ENCOMENDA) {
            this.estoque = null;
        }
    }

    /** Saldo físico completo (nulo para Sob Encomenda). Não é exposto na API. */
    @JsonIgnore
    public Estoque getEstoque() {
        return estoque;
    }

    /**
     * Preço praticado agora: o promocional somente enquanto a promoção está vigente
     * (RN08 reverte automaticamente à meia-noite, mas o checkout nunca confia só no cron).
     */
    public BigDecimal obterPrecoVigente() {
        LocalDateTime agora = LocalDateTime.now();
        boolean promocaoVigente = precoPromocional != null
                && (dataInicioOferta == null || !agora.isBefore(dataInicioOferta))
                && (dataLimitePromocao == null || !agora.isAfter(dataLimitePromocao));
        return promocaoVigente ? precoPromocional : precoBase;
    }

    private Estoque obterOuCriarEstoque() {
        if (this.estoque == null) {
            this.estoque = new Estoque();
            this.estoque.setProduto(this);
        }
        return this.estoque;
    }

    public Integer getQuantidadeEstoque() {
        return estoque == null ? null : estoque.getQuantidade();
    }

    public void setQuantidadeEstoque(Integer quantidadeEstoque) {
        if (quantidadeEstoque == null) {
            this.estoque = null;
            return;
        }
        obterOuCriarEstoque().setQuantidade(quantidadeEstoque);
    }

    public Integer getQuantidadeReservada() {
        return estoque == null ? 0 : estoque.getQuantidadeReservada();
    }

    public void setQuantidadeReservada(Integer quantidadeReservada) {
        obterOuCriarEstoque().setQuantidadeReservada(quantidadeReservada == null ? 0 : quantidadeReservada);
    }

    public LocalDate getDataValidade() {
        return estoque == null ? null : estoque.getDataValidade();
    }

    public void setDataValidade(LocalDate dataValidade) {
        obterOuCriarEstoque().setDataValidade(dataValidade);
    }

    public Integer getPrazoEntregaDias() {
        return PRAZO_ENTREGA_PADRAO_DIAS;
    }

    public Integer getTempoMedioUsoDias() { return tempoMedioUsoDias; }
    public void setTempoMedioUsoDias(Integer tempoMedioUsoDias) { this.tempoMedioUsoDias = tempoMedioUsoDias; }

    public BigDecimal getCustoFabrica() { return custoFabrica; }
    public void setCustoFabrica(BigDecimal custoFabrica) { this.custoFabrica = custoFabrica; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
    public String getMarca() { return marca; }
    public void setMarca(String marca) { this.marca = marca; }
    public Categoria getCategoria() { return categoria; }
    public void setCategoria(Categoria categoria) { this.categoria = categoria; }
    public BigDecimal getPrecoBase() { return precoBase; }
    public void setPrecoBase(BigDecimal precoBase) { this.precoBase = precoBase; }
    public BigDecimal getPrecoPromocional() { return precoPromocional; }
    public void setPrecoPromocional(BigDecimal precoPromocional) { this.precoPromocional = precoPromocional; }
    public LocalDateTime getDataInicioOferta() { return dataInicioOferta; }
    public void setDataInicioOferta(LocalDateTime dataInicioOferta) { this.dataInicioOferta = dataInicioOferta; }
    public LocalDateTime getDataLimitePromocao() { return dataLimitePromocao; }
    public void setDataLimitePromocao(LocalDateTime dataLimitePromocao) { this.dataLimitePromocao = dataLimitePromocao; }
    public ModalidadeProduto getModalidade() { return modalidade; }
    public void setModalidade(ModalidadeProduto modalidade) { this.modalidade = modalidade; }
    public String getImagemUrl() { return imagemUrl; }
    public void setImagemUrl(String imagemUrl) { this.imagemUrl = imagemUrl; }
    public boolean isAtivo() { return ativo; }

    /** A tabela exige deleted_at preenchido sempre que ativo = false. */
    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
        this.deletedAt = ativo ? null : LocalDateTime.now();
    }
}