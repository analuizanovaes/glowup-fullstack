package com.glowup.api.dto;

import com.glowup.api.domain.TipoEntrega;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Entrada do checkout. Os itens vêm do carrinho salvo no servidor e todos os valores (subtotal, desconto,
 * frete, total e prazo) são calculados pelo back-end: o cliente informa apenas a forma de entrega e o cupom.
 * Para DOMICILIO informe enderecoId (endereço salvo no perfil) ou os campos do endereço.
 */
public record CheckoutRequisicaoDTO(
    @NotNull(message = "O aceite dos termos é obrigatório")
    Boolean aceitouTermos,

    @NotBlank(message = "A versão dos termos deve ser informada")
    @Size(max = 20, message = "A versão dos termos deve ter no máximo 20 caracteres")
    String versaoTermos,

    @NotNull(message = "Informe o tipo de entrega (DOMICILIO ou RETIRADA)")
    TipoEntrega tipoEntrega,

    Long enderecoId,

    @Size(max = 60, message = "O país deve ter no máximo 60 caracteres")
    String pais,

    @Size(max = 10, message = "CEP inválido")
    String cep,

    @Size(max = 180, message = "O logradouro deve ter no máximo 180 caracteres")
    String logradouro,

    @Size(max = 30, message = "O número deve ter no máximo 30 caracteres")
    String numero,

    @Size(max = 120, message = "O complemento deve ter no máximo 120 caracteres")
    String complemento,

    @Size(max = 100, message = "O bairro deve ter no máximo 100 caracteres")
    String bairro,

    @Size(max = 100, message = "A cidade deve ter no máximo 100 caracteres")
    String cidade,

    @Size(max = 2, message = "Informe a UF com 2 letras")
    String uf,

    @Size(max = 50, message = "O cupom deve ter no máximo 50 caracteres")
    String codigoCupom
) {}