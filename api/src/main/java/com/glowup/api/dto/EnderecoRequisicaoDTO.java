package com.glowup.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EnderecoRequisicaoDTO(
    @NotBlank(message = "O CEP é obrigatório")
    @Pattern(regexp = "^\\d{5}-?\\d{3}$", message = "CEP inválido")
    String cep,

    @NotBlank(message = "O logradouro é obrigatório")
    @Size(max = 180, message = "O logradouro deve ter no máximo 180 caracteres")
    String logradouro,

    @NotBlank(message = "O número é obrigatório")
    @Size(max = 30, message = "O número deve ter no máximo 30 caracteres")
    String numero,

    @Size(max = 120, message = "O complemento deve ter no máximo 120 caracteres")
    String complemento,

    @NotBlank(message = "O bairro é obrigatório")
    @Size(max = 100, message = "O bairro deve ter no máximo 100 caracteres")
    String bairro,

    @NotBlank(message = "A cidade é obrigatória")
    @Size(max = 100, message = "A cidade deve ter no máximo 100 caracteres")
    String cidade,

    @NotBlank(message = "A UF é obrigatória")
    @Pattern(regexp = "^[A-Za-z]{2}$", message = "Informe a UF com 2 letras")
    String uf,

    Boolean principal
) {}
