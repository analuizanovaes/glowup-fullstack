package com.glowup.api.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record UsuarioRespostaDTO(
    UUID id,
    String nome,
    String email,
    LocalDate dataNascimento,
    List<String> telefones
) {}