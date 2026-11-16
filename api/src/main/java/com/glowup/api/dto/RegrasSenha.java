package com.glowup.api.dto;

/** Regra única de senha forte (RF01), compartilhada por cadastro e redefinição. */
public final class RegrasSenha {

    /** Mín. 8 caracteres, maiúscula, minúscula, número e qualquer símbolo. Máx. 72 (limite do BCrypt). */
    public static final String REGEX_SENHA_FORTE =
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z\\d\\s]).{8,72}$";

    public static final String MENSAGEM_SENHA_FORTE =
            "A senha deve ter de 8 a 72 caracteres, incluindo maiúsculas, minúsculas, números e símbolos especiais";

    public static void validarLimiteBcrypt(String senha) {
        if (senha != null && senha.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("A senha não pode ultrapassar 72 bytes em UTF-8.");
        }
    }

    private RegrasSenha() {
    }
}
