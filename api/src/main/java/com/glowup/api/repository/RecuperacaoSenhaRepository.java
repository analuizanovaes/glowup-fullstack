package com.glowup.api.repository;

import com.glowup.api.model.RecuperacaoSenha;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RecuperacaoSenhaRepository extends JpaRepository<RecuperacaoSenha, Long> {

    Optional<RecuperacaoSenha> findFirstByUsuarioIdAndUtilizadoFalseOrderByCriadoEmDesc(Long usuarioId);

    @Modifying
    @Query("UPDATE RecuperacaoSenha r SET r.utilizado = true, r.utilizadoEm = :agora "
            + "WHERE r.usuario.id = :usuarioId AND r.utilizado = false")
    int invalidarCodigosAtivos(@Param("usuarioId") Long usuarioId, @Param("agora") LocalDateTime agora);
}
