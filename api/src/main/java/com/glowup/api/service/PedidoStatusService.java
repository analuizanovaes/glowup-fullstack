package com.glowup.api.service;

import com.glowup.api.domain.HistoricoStatusPedido;
import com.glowup.api.domain.Pedido;
import com.glowup.api.domain.StatusPedido;
import com.glowup.api.repository.HistoricoStatusPedidoRepository;
import com.glowup.api.repository.PedidoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PedidoStatusService {

    private final PedidoRepository pedidoRepository;
    private final HistoricoStatusPedidoRepository historicoRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void alterar(Pedido pedido, StatusPedido novoStatus, String origem) {
        StatusPedido statusAnterior = pedido.getStatus();
        pedido.setStatus(novoStatus);
        pedidoRepository.save(pedido);

        historicoRepository.save(HistoricoStatusPedido.builder()
                .pedido(pedido)
                .statusAnterior(statusAnterior)
                .statusNovo(novoStatus)
                .origem(origem)
                .ocorridoEm(LocalDateTime.now())
                .build());
    }
}
