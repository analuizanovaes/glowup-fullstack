package com.glowup.api.config;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Paginação das listagens. O corpo continua sendo uma lista; o total vem nos cabeçalhos
 * X-Total-Count e X-Total-Pages. Sem parâmetros, devolve a primeira página de 50.
 */
public final class Paginacao {

    public static final int TAMANHO_PADRAO = 50;
    public static final int TAMANHO_MAXIMO = 100;

    private Paginacao() {
    }

    /** Página e tamanho seguros, sem ordenação: quem chama define a ordem (catálogo: id ou preço vigente). */
    public static Pageable semOrdenacao(Integer pagina, Integer tamanho) {
        int paginaSegura = pagina == null || pagina < 0 ? 0 : pagina;
        int tamanhoSeguro = tamanho == null || tamanho < 1 ? TAMANHO_PADRAO : Math.min(tamanho, TAMANHO_MAXIMO);
        return PageRequest.of(paginaSegura, tamanhoSeguro);
    }

    /** Página começa em 0. Mais recentes primeiro, pelo campo informado. */
    public static Pageable maisRecentesPrimeiro(Integer pagina, Integer tamanho, String campoData) {
        int paginaSegura = pagina == null || pagina < 0 ? 0 : pagina;
        int tamanhoSeguro = tamanho == null || tamanho < 1 ? TAMANHO_PADRAO : Math.min(tamanho, TAMANHO_MAXIMO);
        return PageRequest.of(paginaSegura, tamanhoSeguro, Sort.by(Sort.Direction.DESC, campoData));
    }
}
