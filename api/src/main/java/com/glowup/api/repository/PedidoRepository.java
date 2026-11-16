package com.glowup.api.repository;

import com.glowup.api.domain.Pedido;
import com.glowup.api.domain.StatusPedido;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Pedido p WHERE p.id = :id")
    Optional<Pedido> buscarParaAtualizacao(@Param("id") Long id);
    

    @Query("SELECT SUM(i.precoUnitario * i.quantidade) FROM ItemPedido i WHERE i.pedido.id = :pedidoId")
    BigDecimal somarItens(@Param("pedidoId") Long pedidoId);

    Optional<Pedido> findByIdAndClienteId(Long id, Long clienteId);

    long countByClienteIdAndStatus(Long clienteId, StatusPedido status);

    boolean existsByClienteIdAndStatusIn(Long clienteId, Collection<StatusPedido> status);

    Optional<Pedido> findByClienteIdAndIdempotencyKey(Long clienteId, String idempotencyKey);

    List<Pedido> findByClienteIdOrderByDataCriacaoDesc(Long clienteId);

    Page<Pedido> findByClienteId(Long clienteId, Pageable pageable);

    Page<Pedido> findByStatus(StatusPedido status, Pageable pageable);

    List<Pedido> findAllByOrderByDataCriacaoDesc();

    List<Pedido> findByStatusOrderByDataCriacaoDesc(StatusPedido status);

    @Query("SELECT COUNT(p) FROM Pedido p "
            + "WHERE p.status = :status AND p.dataCriacao >= :inicio AND p.dataCriacao < :fim")
    long contarPorStatusNoPeriodo(@Param("status") StatusPedido status,
                                  @Param("inicio") LocalDateTime inicio,
                                  @Param("fim") LocalDateTime fim);

    @Query("SELECT p.id FROM Pedido p WHERE p.status = :status AND p.dataCriacao < :limite "
            + "AND NOT EXISTS (SELECT g.id FROM Pagamento g WHERE g.pedido.id = p.id)")
    List<Long> buscarIdsSemPagamentoCriadosAntesDe(@Param("status") StatusPedido status,
                                                   @Param("limite") LocalDateTime limite);
}
