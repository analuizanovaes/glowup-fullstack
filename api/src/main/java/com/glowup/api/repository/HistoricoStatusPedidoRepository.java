package com.glowup.api.repository;

import com.glowup.api.domain.HistoricoStatusPedido;
import com.glowup.api.domain.StatusPedido;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HistoricoStatusPedidoRepository extends JpaRepository<HistoricoStatusPedido, Long> {

    Optional<HistoricoStatusPedido> findFirstByPedidoIdAndStatusNovoOrderByOcorridoEmDesc(Long pedidoId,
                                                                                         StatusPedido statusNovo);
}
