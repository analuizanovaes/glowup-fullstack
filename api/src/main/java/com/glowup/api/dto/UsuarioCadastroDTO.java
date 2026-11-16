package com.glowup.api.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;

public record UsuarioCadastroDTO(
    @NotBlank(message = "O nome é obrigatório")
    @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres")
    String nome,

    @NotBlank(message = "O e-mail é obrigatório")
    @Email(message = "Formato de e-mail inválido")
    @Size(max = 150, message = "O e-mail deve ter no máximo 150 caracteres")
    String email,

    @NotBlank(message = "A senha é obrigatória")
    @Pattern(regexp = RegrasSenha.REGEX_SENHA_FORTE, message = RegrasSenha.MENSAGEM_SENHA_FORTE)
    String senha,

    @NotNull(message = "A data de nascimento é obrigatória")
    @Past(message = "A data de nascimento deve estar no passado")
    LocalDate dataNascimento,

    @NotEmpty(message = "É obrigatório informar pelo menos um telefone")
    @Size(max = 5, message = "Informe no máximo 5 telefones")
    List<@NotBlank(message = "O número do telefone não pode ser vazio")
         @Pattern(regexp = "^[\\d\\s()+-]{8,20}$", message = "Telefone inválido") String> telefones,

    @AssertTrue(message = "É obrigatório aceitar os Termos de Uso e declarar ser maior de 18 anos")
    boolean aceitaTermos,

    /** Versão dos termos exibida à cliente (GET /api/termos/versao-atual). Opcional: sem ela vale a vigente. */
    @Size(max = 20, message = "A versão dos termos deve ter no máximo 20 caracteres")
    String versaoTermos
) {
    /** Compatibilidade: cadastro sem informar a versão dos termos. */
    public UsuarioCadastroDTO(String nome, String email, String senha, LocalDate dataNascimento,
                              List<String> telefones, boolean aceitaTermos) {
        this(nome, email, senha, dataNascimento, telefones, aceitaTermos, null);
    }
}