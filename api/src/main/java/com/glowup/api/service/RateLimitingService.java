package com.glowup.api.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limite de requisições por chave (grupo de rota + IP), todos na mesma janela. Os baldes ficam em memória e os que
 * já recarregaram por completo são removidos periodicamente, para o mapa não crescer sem fim.
 */
@Service
public class RateLimitingService {

    private record Entrada(Bucket bucket, long capacidade) {
    }

    private final Map<String, Entrada> cache = new ConcurrentHashMap<>();
    private final long capacidadeAutenticacao;
    private final long capacidadePedidos;
    private final long capacidadeCep;
    private final long capacidadeSincronizacao;
    private final Duration janela;

    /** Webhook público do Mercado Pago: chamadas por minuto e por IP (teto contra abuso, bem acima do uso real). */
    @Value("${glowup.ratelimit.webhook-max-por-minuto:120}")
    private long capacidadeWebhookPorMinuto = 120;

    public RateLimitingService(@Value("${glowup.ratelimit.auth-max:20}") long capacidadeAutenticacao,
                               @Value("${glowup.ratelimit.pedido-max:30}") long capacidadePedidos,
                               @Value("${glowup.ratelimit.cep-max:100}") long capacidadeCep,
                               @Value("${glowup.ratelimit.auth-janela-minutos:15}") long janelaMinutos,
                               @Value("${glowup.ratelimit.sincronizar-max-por-minuto:12}") long capacidadeSincronizacao) {
        this.capacidadeAutenticacao = capacidadeAutenticacao;
        this.capacidadePedidos = capacidadePedidos;
        this.capacidadeCep = capacidadeCep;
        this.capacidadeSincronizacao = capacidadeSincronizacao;
        this.janela = Duration.ofMinutes(janelaMinutos);
    }

    /** Login, refresh, recuperação/troca de senha e cadastro. */
    public Bucket resolveBucket(String chave) {
        return resolver(chave, capacidadeAutenticacao);
    }

    /** Checkout e pagamento: evita segurar estoque em massa e martelar o gateway. */
    public Bucket resolveBucketPedidos(String chave) {
        return resolver("pedidos:" + chave, capacidadePedidos);
    }

    /** Consulta de CEP: cada chamada aciona o ViaCEP. */
    public Bucket resolveBucketCep(String chave) {
        return resolver("cep:" + chave, capacidadeCep);
    }

    /** Sincronização de pagamento: cada chamada consulta o Mercado Pago. Janela curta (1 min) para permitir polling. */
    public Bucket resolveBucketSincronizacao(String chave) {
        return resolver("sincronizar:" + chave, capacidadeSincronizacao, Duration.ofMinutes(1));
    }

    /** Webhook do Mercado Pago: rota pública, validada por assinatura; o limite só segura inundação de requisições. */
    public Bucket resolveBucketWebhook(String chave) {
        return resolver("webhook:" + chave, capacidadeWebhookPorMinuto, Duration.ofMinutes(1));
    }

    private Bucket resolver(String chave, long capacidade) {
        return resolver(chave, capacidade, janela);
    }

    private Bucket resolver(String chave, long capacidade, Duration janelaDoBalde) {
        return cache.computeIfAbsent(chave, k -> new Entrada(novoBucket(capacidade, janelaDoBalde), capacidade)).bucket();
    }

    private Bucket novoBucket(long capacidade, Duration janelaDoBalde) {
        Bandwidth limite = Bandwidth.classic(capacidade, Refill.greedy(capacidade, janelaDoBalde));
        return Bucket.builder().addLimit(limite).build();
    }

    /** Baldes cheios = sem atividade recente: podem sair da memória sem mudar o comportamento. */
    @Scheduled(fixedDelay = 3_600_000)
    void limparBaldesInativos() {
        cache.entrySet().removeIf(e -> e.getValue().bucket().getAvailableTokens() >= e.getValue().capacidade());
    }
}
