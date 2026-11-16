package com.glowup.api.dto;

import com.glowup.api.domain.StatusReposicao;
import jakarta.validation.constraints.NotNull;

public record StatusReposicaoDTO(
    @NotNull(message = "O status é obrigatório (PENDENTE, CONTATADA ou DESCARTADA)")
    StatusReposicao status
) {}
