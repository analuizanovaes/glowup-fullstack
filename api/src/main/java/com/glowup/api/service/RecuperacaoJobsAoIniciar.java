package com.glowup.api.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Se a instância ficar fora do ar, os CRONs da meia-noite e das 8h não disparam enquanto ela dorme.
 * Ao subir, executa as rotinas diárias uma vez. Elas são idempotentes (não duplicam alertas nem sugestões),
 * então rodar de novo é seguro. O job de expiração de pagamentos já roda em intervalo fixo e se recupera sozinho.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RecuperacaoJobsAoIniciar {

    private final AutomacaoCronService automacaoCronService;

    @Value("${glowup.jobs.recuperar-ao-subir:true}")
    private boolean habilitado;

    @EventListener(ApplicationReadyEvent.class)
    public void recuperar() {
        if (!habilitado) {
            return;
        }
        log.info("Recuperação de rotinas diárias ao iniciar a API.");
        executar("promoções expiradas", automacaoCronService::reverterPromocoesExpiradas);
        executar("alertas de validade", automacaoCronService::verificarVencimentoProdutos);
        executar("sugestões de reposição (CRM)", automacaoCronService::verificarSugestaoReposicaoCRM);
    }

    private void executar(String nome, java.util.function.IntSupplier rotina) {
        try {
            rotina.getAsInt();
        } catch (RuntimeException excecao) {
            // Uma rotina com falha não pode impedir a API de funcionar nem as demais rotinas de rodar.
            log.error("Falha na recuperação da rotina '{}': {}", nome, excecao.getMessage());
        }
    }
}
