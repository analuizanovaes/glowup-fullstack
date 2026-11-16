package com.glowup.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RedefinicaoSenhaDTO(
    @NotBlank(message = "O e-mail é obrigatório")
    @Email(message = "Formato de e-mail inválido")
    String email,

    @NotBlank(message = "O código OTP é obrigatório")
    @Pattern(regexp = "^\\d{6}$", message = "O código OTP deve ter 6 dígitos")
    String codigoOtp,

    @NotBlank(message = "A nova senha é obrigatória")
    @Pattern(regexp = RegrasSenha.REGEX_SENHA_FORTE, message = RegrasSenha.MENSAGEM_SENHA_FORTE)
    String novaSenha
) {}