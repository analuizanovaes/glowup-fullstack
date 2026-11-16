package com.glowup.api.repository;

import com.glowup.api.domain.Parcela;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ParcelaRepository extends JpaRepository<Parcela, Long> {

    boolean existsByPagamentoId(Long pagamentoId);

    List<Parcela> findByPagamentoIdOrderByNumero(Long pagamentoId);
}
