package com.glowup.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A data de nascimento não é editável: ela sustenta a trava de maioridade (RN07).
 * senhaAtual só é exigida quando o e-mail muda: o e-mail recebe o código de recuperação de senha.
 */
public record PerfilAtualizacaoDTO(
    @NotBlank(message = "O nome é obrigatório")
    @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres")
    String nome,

    @NotBlank(message = "O e-mail é obrigatório")
    @Email(message = "Formato de e-mail inválido")
    @Size(max = 150, message = "O e-mail deve ter no máximo 150 caracteres")
    String email,

    @Size(max = 72, message = "A senha atual deve ter no máximo 72 caracteres")
    String senhaAtual
) {}
