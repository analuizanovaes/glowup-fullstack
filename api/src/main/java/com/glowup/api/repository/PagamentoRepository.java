package com.glowup.api.repository;

import com.glowup.api.domain.MetodoPagamento;
import com.glowup.api.domain.Pagamento;
import com.glowup.api.domain.StatusPagamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PagamentoRepository extends JpaRepository<Pagamento, Long> {

    Optional<Pagamento> findByPedidoId(Long pedidoId);

    Optional<Pagamento> findByIdGateway(String idGateway);

    boolean existsByPedidoId(Long pedidoId);

    @Query("SELECT p.pedido.id FROM Pagamento p WHERE p.id = :pagamentoId")
    Long buscarIdDoPedido(@Param("pagamentoId") Long pagamentoId);

    @Query("SELECT p.pedido.id FROM Pagamento p "
            + "WHERE p.metodo = :metodo AND p.status = :status AND p.pixExpiraEm < :limite")
    List<Long> buscarPedidosComPagamentoExpirado(@Param("metodo") MetodoPagamento metodo,
                                                 @Param("status") StatusPagamento status,
                                                 @Param("limite") LocalDateTime limite);
}
