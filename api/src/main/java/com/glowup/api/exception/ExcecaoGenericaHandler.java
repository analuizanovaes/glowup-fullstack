package com.glowup.api.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Último recurso: tudo que nenhum outro handler tratou. Erros padrão do Spring (rota inexistente,
 * método não permitido, upload grande demais etc.) mantêm seu status; o restante vira 500 sem vazar detalhes.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
@Slf4j
public class ExcecaoGenericaHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroResposta> tratarExcecaoNaoPrevista(Exception excecao) {
        if (excecao instanceof ErrorResponse erroDoSpring) {
            int status = erroDoSpring.getStatusCode().value();
            return ErroResposta.de(status, mensagemPadrao(status));
        }
        log.error("Erro interno não tratado", excecao);
        return ErroResposta.de(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno no servidor. Tente novamente mais tarde.");
    }

    private String mensagemPadrao(int status) {
        return switch (status) {
            case 400 -> "Requisição inválida. Verifique os dados enviados.";
            case 404 -> "Rota ou recurso não encontrado.";
            case 405 -> "Método HTTP não permitido para esta rota.";
            case 406 -> "Formato de resposta não suportado.";
            case 413 -> "O arquivo enviado excede o tamanho máximo permitido.";
            case 415 -> "Tipo de conteúdo não suportado. Verifique o cabeçalho Content-Type.";
            default -> "A requisição não pôde ser processada.";
        };
    }
}
