package com.glowup.api.service;

import com.glowup.api.domain.Devolucao;
import com.glowup.api.domain.Estorno;
import com.glowup.api.domain.Pagamento;
import com.glowup.api.domain.StatusEstorno;
import com.glowup.api.exception.ConflitoDeEstadoException;
import com.glowup.api.repository.EstornoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class EstornoService {

    private final EstornoRepository estornoRepository;

    /** Chamado só depois que o gateway confirmou o estorno. Uma devolução tem no máximo um estorno. */
    @Transactional
    public Estorno registrarConcluido(Devolucao devolucao, Pagamento pagamento, BigDecimal valor) {
        if (estornoRepository.existsByDevolucaoId(devolucao.getId())) {
            throw new ConflitoDeEstadoException("Esta devolução já possui estorno registrado.");
        }
        return estornoRepository.save(Estorno.builder()
                .devolucao(devolucao)
                .pagamento(pagamento)
                .valor(valor)
                .motivo("Estorno da devolução #" + devolucao.getId())
                .status(StatusEstorno.CONCLUIDO)
                .processadoEm(LocalDateTime.now())
                .build());
    }
}
