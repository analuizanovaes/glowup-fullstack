package com.glowup.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RedefinicaoSenhaDTO(
    @NotBlank(message = "O e-mail é obrigatório")
    @Email(message = "Formato de e-mail inválido")
    String email,

    @NotBlank(message = "O código OTP é obrigatório")
    @Size(min = 6, max = 6, message = "O código OTP deve ter 6 dígitos")
    String codigoOtp,

    @NotBlank(message = "A nova senha é obrigatória")
    @Pattern(
        regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[@$!%*?&#])[A-Za-z\\d@$!%*?&#]{8,}$",
        message = "A senha deve ter no mínimo 8 caracteres, incluindo maiúsculas, minúsculas, números e símbolos especiais"
    )
    String novaSenha
) {}