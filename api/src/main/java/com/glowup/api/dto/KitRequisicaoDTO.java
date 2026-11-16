package com.glowup.api.dto;

import com.glowup.api.domain.ModalidadeProduto;
import com.glowup.api.domain.TipoKit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Cadastro de kit (somente administradora). FABRICA tem estoque próprio (quantidadeEstoque);
 * MANUAL é montado a partir de componentes avulsos (itens) e baixa o estoque deles.
 */
public record KitRequisicaoDTO(
    @NotBlank(message = "O nome é obrigatório")
    @Size(max = 180, message = "O nome deve ter no máximo 180 caracteres")
    String nome,

    String descricao,

    @NotBlank(message = "A marca é obrigatória")
    @Size(max = 80, message = "A marca deve ter no máximo 80 caracteres")
    String marca,

    @NotBlank(message = "A categoria é obrigatória")
    String categoria,

    @NotNull(message = "O custo de fábrica é obrigatório")
    @DecimalMin(value = "0.00", message = "O custo de fábrica não pode ser negativo")
    BigDecimal custoFabrica,

    @NotNull(message = "O preço base é obrigatório")
    @DecimalMin(value = "0.00", message = "O preço base não pode ser negativo")
    BigDecimal precoBase,

    @DecimalMin(value = "0.00", message = "O preço promocional não pode ser negativo")
    BigDecimal precoPromocional,

    LocalDateTime dataInicioOferta,

    LocalDateTime dataLimitePromocao,

    @NotNull(message = "A modalidade é obrigatória (PRONTA_ENTREGA ou SOB_ENCOMENDA)")
    ModalidadeProduto modalidade,

    @NotNull(message = "O tipo do kit é obrigatório (FABRICA ou MANUAL)")
    TipoKit tipo,

    @Min(value = 0, message = "A quantidade em estoque não pode ser negativa")
    Integer quantidadeEstoque,

    @Valid
    List<ItemKitRequisicaoDTO> itens
) {}
