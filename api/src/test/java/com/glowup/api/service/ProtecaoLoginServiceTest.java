package com.glowup.api.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtecaoLoginServiceTest {

    // 3 erros na janela de 15 min bloqueiam a conta por 15 min
    private final ProtecaoLoginService protecao = new ProtecaoLoginService(3, 15, 15);

    @Test
    @DisplayName("Bloqueia a conta ao atingir o limite de senhas erradas")
    void bloqueiaAoAtingirOLimite() {
        protecao.registrarFalha("cliente@teste.com");
        protecao.registrarFalha("cliente@teste.com");
        assertFalse(protecao.estaBloqueado("cliente@teste.com"));

        protecao.registrarFalha("cliente@teste.com");
        assertTrue(protecao.estaBloqueado("cliente@teste.com"));
    }

    @Test
    @DisplayName("O bloqueio de uma conta não afeta as outras")
    void contasSaoIndependentes() {
        for (int i = 0; i < 3; i++) {
            protecao.registrarFalha("a@teste.com");
        }
        assertTrue(protecao.estaBloqueado("a@teste.com"));
        assertFalse(protecao.estaBloqueado("b@teste.com"));
    }

    @Test
    @DisplayName("Login correto zera a contagem de erros")
    void sucessoZeraAContagem() {
        protecao.registrarFalha("cliente@teste.com");
        protecao.registrarFalha("cliente@teste.com");
        protecao.registrarSucesso("cliente@teste.com");
        protecao.registrarFalha("cliente@teste.com");
        protecao.registrarFalha("cliente@teste.com");

        assertFalse(protecao.estaBloqueado("cliente@teste.com"));
    }
}
