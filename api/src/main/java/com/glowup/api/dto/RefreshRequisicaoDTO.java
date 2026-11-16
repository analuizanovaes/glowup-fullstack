package com.glowup.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshRequisicaoDTO(
    @NotBlank(message = "O refresh token é obrigatório")
    @Size(max = 200, message = "Refresh token inválido")
    String refreshToken
) {}
