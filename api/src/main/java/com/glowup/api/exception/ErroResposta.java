package com.glowup.api.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

/**
 * Corpo único de erro da API. O campo "erro" repete "message" por compatibilidade com
 * consumidores que já o utilizam; "campos" só aparece em falhas de validação.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErroResposta(int status, String message, String erro, Map<String, String> campos) {

    public static ResponseEntity<ErroResposta> de(int status, String mensagem) {
        return ResponseEntity.status(status).body(new ErroResposta(status, mensagem, mensagem, null));
    }

    public static ResponseEntity<ErroResposta> de(HttpStatus status, String mensagem) {
        return de(status.value(), mensagem);
    }

    public static ResponseEntity<ErroResposta> deValidacao(Map<String, String> campos) {
        int status = HttpStatus.BAD_REQUEST.value();
        String mensagem = String.join("; ", campos.values());
        return ResponseEntity.status(status).body(new ErroResposta(status, mensagem, mensagem, campos));
    }
}
