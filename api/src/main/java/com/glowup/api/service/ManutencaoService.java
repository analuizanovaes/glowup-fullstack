package com.glowup.api.service;

import com.glowup.api.model.ConfiguracaoSistema;
import com.glowup.api.repository.ConfiguracaoSistemaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ManutencaoService {

    private static final Logger logger = LoggerFactory.getLogger(ManutencaoService.class);
    private static final String CHAVE_MANUTENCAO = "MODO_MANUTENCAO";

    private final ConfiguracaoSistemaRepository repository;

    public ManutencaoService(ConfiguracaoSistemaRepository repository) {
        this.repository = repository;
    }

    private static final long VALIDADE_CACHE_MS = 5_000;

    private volatile boolean cacheAtivo = false;
    private volatile long cacheExpiraEm = 0;

    // Retorna true se o valor no banco for "true". O resultado fica em cache por 5 s para não consultar o
    // banco em toda requisição; alterarStatus() invalida o cache na hora.
    public boolean isModoManutencaoAtivo() {
        long agora = System.currentTimeMillis();
        if (agora < cacheExpiraEm) {
            return cacheAtivo;
        }
        boolean ativo = repository.findById(CHAVE_MANUTENCAO)
                .map(config -> Boolean.parseBoolean(config.getValor()))
                .orElse(false);
        cacheAtivo = ativo;
        cacheExpiraEm = agora + VALIDADE_CACHE_MS;
        return ativo;
    }

    // Altera o estado e registra auditoria de segurança
    public void alterarStatus(boolean ativar, String adminEmail, String ip) {
        ConfiguracaoSistema config = new ConfiguracaoSistema(CHAVE_MANUTENCAO, String.valueOf(ativar));
        repository.save(config);
        cacheAtivo = ativar;
        cacheExpiraEm = System.currentTimeMillis() + VALIDADE_CACHE_MS;
        
        // Sanitização e registro de log
        String acao = ativar ? "ATIVADO" : "DESATIVADO";
        logger.warn("Modo de Manutencao {} por Admin: {} | IP: {}", acao, adminEmail, ip);
    }
}
