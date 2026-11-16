package com.glowup.api.repository;

import com.glowup.api.domain.Cupom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CupomRepository extends JpaRepository<Cupom, Long> {

    Optional<Cupom> findByCodigoIgnoreCase(String codigo);

    boolean existsByCodigoIgnoreCase(String codigo);

    /** Consome um uso de forma atômica; retorna 0 quando o limite de usos já foi atingido. */
    @Modifying
    @Query("UPDATE Cupom c SET c.usosRealizados = c.usosRealizados + 1 "
         + "WHERE c.id = :id AND (c.limiteUso IS NULL OR c.usosRealizados < c.limiteUso)")
    int registrarUso(@Param("id") Long id);

    @Modifying
    @Query("UPDATE Cupom c SET c.usosRealizados = c.usosRealizados - 1 WHERE c.id = :id AND c.usosRealizados > 0")
    int liberarUso(@Param("id") Long id);
}
