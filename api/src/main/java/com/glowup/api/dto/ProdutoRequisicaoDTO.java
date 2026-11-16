package com.glowup.api.dto;

import com.glowup.api.domain.ModalidadeProduto;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Cadastro/edição de produto (RF02). Para PRONTA_ENTREGA a quantidadeEstoque é obrigatória;
 * para SOB_ENCOMENDA ela é ignorada (sem controle físico).
 */
public record ProdutoRequisicaoDTO(
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

    @Min(value = 0, message = "A quantidade em estoque não pode ser negativa")
    Integer quantidadeEstoque,

    LocalDate dataValidade,

    @Min(value = 1, message = "O tempo médio de uso deve ser de pelo menos 1 dia")
    Integer tempoMedioUsoDias,

    @Size(max = 500, message = "A URL da imagem deve ter no máximo 500 caracteres")
    @Pattern(regexp = "^(https://\\S+|assets/img/produtos/[A-Za-z0-9._-]+)?$", message = "A URL da imagem deve começar com https://")
    String imagemUrl
) {}
