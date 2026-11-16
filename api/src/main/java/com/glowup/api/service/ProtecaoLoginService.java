package com.glowup.api.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bloqueio de login por conta (e-mail), complementar ao limite por IP do {@link RateLimitingService}: o limite por IP
 * não segura quem tenta uma mesma conta a partir de muitos IPs. Depois de N senhas erradas dentro da janela, a conta
 * fica bloqueada por alguns minutos (mesmo para a senha correta, senão o bloqueio não protegeria nada).
 * <p>
 * A contagem vale para qualquer e-mail informado, exista a conta ou não, para a resposta não revelar quais contas
 * existem. O estado fica em memória e os registros expirados são removidos periodicamente.
 */
@Service
public class ProtecaoLoginService {

    /** Teto de e-mails distintos rastreados ao mesmo tempo: impede que e-mails aleatórios encham a memória. */
    private static final int MAXIMO_DE_CONTAS_RASTREADAS = 50_000;

    private record Registro(int falhas, long inicioDaJanelaMs, long bloqueadoAteMs) {
    }

    private final Map<String, Registro> registros = new ConcurrentHashMap<>();
    private final int maximoDeFalhas;
    private final long janelaMs;
    private final long bloqueioMs;

    public ProtecaoLoginService(@Value("${glowup.login.max-falhas:10}") int maximoDeFalhas,
                                @Value("${glowup.login.janela-minutos:15}") long janelaMinutos,
                                @Value("${glowup.login.bloqueio-minutos:15}") long bloqueioMinutos) {
        this.maximoDeFalhas = maximoDeFalhas;
        this.janelaMs = janelaMinutos * 60_000L;
        this.bloqueioMs = bloqueioMinutos * 60_000L;
    }

    /** true se a conta está bloqueada neste instante. */
    public boolean estaBloqueado(String email) {
        Registro registro = registros.get(email);
        return registro != null && registro.bloqueadoAteMs() > System.currentTimeMillis();
    }

    /** Conta mais uma senha errada; ao atingir o limite dentro da janela, bloqueia a conta. */
    public void registrarFalha(String email) {
        long agora = System.currentTimeMillis();
        if (!registros.containsKey(email) && registros.size() >= MAXIMO_DE_CONTAS_RASTREADAS) {
            return;
        }
        registros.compute(email, (chave, atual) -> {
            if (atual == null || agora - atual.inicioDaJanelaMs() > janelaMs) {
                atual = new Registro(0, agora, 0L);
            }
            int falhas = atual.falhas() + 1;
            long bloqueadoAte = falhas >= maximoDeFalhas ? agora + bloqueioMs : atual.bloqueadoAteMs();
            return new Registro(falhas, atual.inicioDaJanelaMs(), bloqueadoAte);
        });
    }

    /** Login correto: zera a contagem da conta. */
    public void registrarSucesso(String email) {
        registros.remove(email);
    }

    /** Remove registros cuja janela e cujo bloqueio já acabaram. */
    @Scheduled(fixedDelay = 3_600_000)
    void limparExpirados() {
        long agora = System.currentTimeMillis();
        registros.entrySet().removeIf(e -> e.getValue().bloqueadoAteMs() <= agora
                && agora - e.getValue().inicioDaJanelaMs() > janelaMs);
    }
}
