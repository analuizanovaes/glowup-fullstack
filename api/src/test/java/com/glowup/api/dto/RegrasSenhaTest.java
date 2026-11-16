package com.glowup.api.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegrasSenhaTest {

    @ParameterizedTest
    @ValueSource(strings = {"Senha@123", "Senha.Forte1", "aB3_efgh", "Gl0wUp#2026"})
    @DisplayName("Aceita senhas com 8+ caracteres, maiúscula, minúscula, número e símbolo")
    void deveAceitarSenhasFortes(String senha) {
        assertTrue(Pattern.matches(RegrasSenha.REGEX_SENHA_FORTE, senha));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Senha@1", "senha@123", "SENHA@123", "Senha1234", "Senha@abc"})
    @DisplayName("Rejeita senhas curtas ou sem maiúscula, minúscula, número ou símbolo")
    void deveRejeitarSenhasFracas(String senha) {
        assertFalse(Pattern.matches(RegrasSenha.REGEX_SENHA_FORTE, senha));
    }
}
