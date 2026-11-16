package com.glowup.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record TelefoneRequisicaoDTO(
    @NotBlank(message = "O número do telefone é obrigatório")
    @Pattern(regexp = "^[\\d\\s()+-]{8,20}$", message = "Telefone inválido")
    String numero
) {}
