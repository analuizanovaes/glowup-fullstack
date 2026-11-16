package com.glowup.api.exception;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingRequestHeaderException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE - 1)
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErroResposta> handleIllegalArgumentException(IllegalArgumentException ex) {
        return ErroResposta.de(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroResposta> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> campos = new LinkedHashMap<>();
        for (ObjectError error : ex.getBindingResult().getAllErrors()) {
            String campo = error instanceof FieldError erroDeCampo ? erroDeCampo.getField() : error.getObjectName();
            campos.put(campo, error.getDefaultMessage());
        }
        return ErroResposta.deValidacao(campos);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErroResposta> handleTipoDeParametroInvalido(MethodArgumentTypeMismatchException ex) {
        return ErroResposta.de(HttpStatus.BAD_REQUEST, "Valor inválido para o parâmetro '" + ex.getName() + "'.");
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErroResposta> handleCabecalhoAusente(MissingRequestHeaderException ex) {
        return ErroResposta.de(HttpStatus.BAD_REQUEST,
                "O cabeçalho obrigatório '" + ex.getHeaderName() + "' não foi informado.");
    }

    @ExceptionHandler(EmailJaCadastradoException.class)
    public ResponseEntity<ErroResposta> handleEmailJaCadastrado(EmailJaCadastradoException ex) {
        return ErroResposta.de(HttpStatus.CONFLICT, ex.getMessage());
    }

    // Rede de segurança: nunca devolve nome de constraint ou SQL ao cliente.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErroResposta> handleViolacaoDeIntegridade(DataIntegrityViolationException ex) {
        return ErroResposta.de(HttpStatus.CONFLICT, "A operação conflita com dados já existentes.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErroResposta> handleAcessoNegado(AccessDeniedException ex) {
        return ErroResposta.de(HttpStatus.FORBIDDEN, "Você não tem permissão para acessar este recurso.");
    }

    @ExceptionHandler(ArmazenamentoImagemException.class)
    public ResponseEntity<ErroResposta> handleArmazenamentoImagem(ArmazenamentoImagemException ex) {
        return ErroResposta.de(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }
}
