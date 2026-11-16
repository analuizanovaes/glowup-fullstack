package com.glowup.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Confirmação da exclusão da própria conta: exige a senha atual, então um token roubado não basta. */
public record ExclusaoContaDTO(
    @NotBlank(message = "A senha é obrigatória para excluir a conta")
    @Size(max = 72, message = "A senha deve ter no máximo 72 caracteres")
    String senha
) {}
