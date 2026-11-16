package com.glowup.api.exception;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FinanceiroExceptionHandler {

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<ErroResposta> tratarRecursoNaoEncontrado(RecursoNaoEncontradoException excecao) {
        return ErroResposta.de(HttpStatus.NOT_FOUND, excecao.getMessage());
    }

    @ExceptionHandler(ConflitoDeEstadoException.class)
    public ResponseEntity<ErroResposta> tratarConflitoDeEstado(ConflitoDeEstadoException excecao) {
        return ErroResposta.de(HttpStatus.CONFLICT, excecao.getMessage());
    }

    @ExceptionHandler(GatewayIndisponivelException.class)
    public ResponseEntity<ErroResposta> tratarGatewayIndisponivel(GatewayIndisponivelException excecao) {
        return ErroResposta.de(HttpStatus.SERVICE_UNAVAILABLE, excecao.getMessage());
    }

    @ExceptionHandler(GatewayRecusouRequisicaoException.class)
    public ResponseEntity<ErroResposta> tratarGatewayRecusou(GatewayRecusouRequisicaoException excecao) {
        return ErroResposta.de(HttpStatus.UNPROCESSABLE_CONTENT, excecao.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErroResposta> tratarCorpoIlegivel(HttpMessageNotReadableException excecao) {
        return ErroResposta.de(HttpStatus.BAD_REQUEST,
                "Corpo da requisição inválido. Verifique o JSON e os valores permitidos.");
    }
}
