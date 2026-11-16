package com.glowup.api.dto;

/**
 * token = access token (JWT de vida curta); refreshToken = renova a sessão em POST /api/auth/refresh;
 * trocaSenhaObrigatoria = true na primeira entrada com senha provisória: o front deve abrir a tela de troca de senha.
 */
public record TokenRespostaDTO(String token, String refreshToken, boolean trocaSenhaObrigatoria) {

    public TokenRespostaDTO(String token) {
        this(token, null, false);
    }
}
