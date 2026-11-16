package com.glowup.api.exception;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE - 2)
public class AutenticacaoExceptionHandler {

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErroResposta> tratarCredenciaisInvalidas(BadCredentialsException excecao) {
        return ErroResposta.de(HttpStatus.UNAUTHORIZED, "E-mail ou senha inválidos.");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErroResposta> tratarFalhaDeAutenticacao(AuthenticationException excecao) {
        return ErroResposta.de(HttpStatus.UNAUTHORIZED, "Falha de autenticação.");
    }
}
