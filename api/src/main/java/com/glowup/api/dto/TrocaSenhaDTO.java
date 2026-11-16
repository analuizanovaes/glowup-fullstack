package com.glowup.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TrocaSenhaDTO(
    @NotBlank(message = "A senha atual é obrigatória")
    @Size(max = 72, message = "A senha atual deve ter no máximo 72 caracteres")
    String senhaAtual,

    @NotBlank(message = "A nova senha é obrigatória")
    @Pattern(regexp = RegrasSenha.REGEX_SENHA_FORTE, message = RegrasSenha.MENSAGEM_SENHA_FORTE)
    String novaSenha
) {}
