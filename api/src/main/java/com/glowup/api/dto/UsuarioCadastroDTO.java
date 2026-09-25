package com.glowup.api.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record UsuarioCadastroDTO(
    @NotBlank(message = "O nome é obrigatório")
    String nome,

    @NotBlank(message = "O e-mail é obrigatório")
    @Email(message = "Formato de e-mail inválido")
    String email,

    @NotBlank(message = "A senha é obrigatória")
    @Pattern(
        regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[@$!%*?&#])[A-Za-z\\d@$!%*?&#]{8,}$",
        message = "A senha deve ter no mínimo 8 caracteres, incluindo maiúsculas, minúsculas, números e símbolos especiais"
    )
    String senha,

    @NotNull(message = "A data de nascimento é obrigatória")
    @Past(message = "A data de nascimento deve estar no passado")
    LocalDate dataNascimento,

    @AssertTrue(message = "É obrigatório aceitar os Termos de Uso e declarar ser maior de 18 anos")
    boolean aceitaTermos
) {}