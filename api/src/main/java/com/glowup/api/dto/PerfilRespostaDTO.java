package com.glowup.api.dto;

import java.time.LocalDate;
import java.util.List;

/** novoToken só vem preenchido quando o e-mail mudou (o JWT é emitido com o e-mail como assunto). */
public record PerfilRespostaDTO(
    Long id,
    String nome,
    String email,
    LocalDate dataNascimento,
    List<TelefoneRespostaDTO> telefones,
    List<EnderecoRespostaDTO> enderecos,
    String novoToken,
    boolean trocaSenhaObrigatoria
) {}
