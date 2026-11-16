package com.glowup.api.exception;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE - 3)
public class GlowUpExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErroResposta> tratarResponseStatusException(ResponseStatusException ex) {
        return ErroResposta.de(ex.getStatusCode().value(), ex.getReason());
    }
}
