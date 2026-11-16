package com.glowup.api.exception;

/** Mensagem única para código errado, expirado ou já usado: não revela qual das condições ocorreu. */
public class CodigoOtpInvalidoException extends IllegalArgumentException {

    public CodigoOtpInvalidoException() {
        super("Código inválido, expirado ou já utilizado. Solicite um novo código.");
    }
}
