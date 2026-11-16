package com.glowup.api.repository;

import com.glowup.api.domain.AlertaValidade;
import com.glowup.api.domain.StatusAlertaValidade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AlertaValidadeRepository extends JpaRepository<AlertaValidade, Long> {

    @Query("SELECT a FROM AlertaValidade a JOIN FETCH a.estoque e JOIN FETCH e.produto p "
         + "WHERE a.status = :status ORDER BY a.diasRestantes ASC")
    List<AlertaValidade> listarPorStatus(@Param("status") StatusAlertaValidade status);

    boolean existsByEstoqueIdAndStatus(Long estoqueId, StatusAlertaValidade status);

    boolean existsByEstoqueId(Long estoqueId);

    boolean existsByEstoqueIdAndDataPrevista(Long estoqueId, java.time.LocalDate dataPrevista);
}
