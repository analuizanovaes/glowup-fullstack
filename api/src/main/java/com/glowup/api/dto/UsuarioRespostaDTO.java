package com.glowup.api.dto;

import java.time.LocalDate;
import java.util.List;

public record UsuarioRespostaDTO(
    Long id,
    String nome,
    String email,
    LocalDate dataNascimento,
    List<String> telefones
) {}
