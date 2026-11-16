package com.glowup.api.repository;

import com.glowup.api.domain.Estorno;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EstornoRepository extends JpaRepository<Estorno, Long> {

    boolean existsByDevolucaoId(Long devolucaoId);
}
