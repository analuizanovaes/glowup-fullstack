package com.glowup.api.repository;

import com.glowup.api.domain.Devolucao;
import com.glowup.api.domain.StatusDevolucao;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DevolucaoRepository extends JpaRepository<Devolucao, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Devolucao d WHERE d.id = :id")
    Optional<Devolucao> buscarParaAtualizacao(@Param("id") Long id);

    boolean existsByPedidoIdAndStatusIn(Long pedidoId, Collection<StatusDevolucao> status);

    List<Devolucao> findByPedidoIdAndStatus(Long pedidoId, StatusDevolucao status);

    List<Devolucao> findByUsuarioIdOrderByDataSolicitacaoDesc(Long usuarioId);

    List<Devolucao> findAllByOrderByDataSolicitacaoDesc();

    List<Devolucao> findByStatusOrderByDataSolicitacaoDesc(StatusDevolucao status);
}
