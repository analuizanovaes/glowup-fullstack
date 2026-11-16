package com.glowup.api.exception;

/**
 * Falha de comunicação com o gateway de pagamento (timeout, erro 5xx ou credencial inválida).
 * Não altera pedido nem carrinho, permitindo que a cliente tente novamente (RN06).
 */
public class GatewayIndisponivelException extends RuntimeException {

    public GatewayIndisponivelException(String mensagem) {
        super(mensagem);
    }
}
