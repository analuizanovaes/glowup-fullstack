package com.glowup.api.exception;

public class EmailJaCadastradoException extends RuntimeException {

    public EmailJaCadastradoException() {
        super("O e-mail informado já está cadastrado na plataforma.");
    }
}
