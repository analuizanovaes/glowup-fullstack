package com.glowup.api.repository;

import com.glowup.api.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RefreshToken r WHERE r.tokenHash = :tokenHash")
    Optional<RefreshToken> buscarParaRotacao(@Param("tokenHash") String tokenHash);

    /** Consome o token de forma atômica: retorna 0 se outra requisição já o usou ou revogou. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken r SET r.revogado = true WHERE r.id = :id AND r.revogado = false")
    int revogarSeAtivo(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken r SET r.revogado = true WHERE r.usuarioId = :usuarioId AND r.revogado = false")
    int revogarTodosDoUsuario(@Param("usuarioId") Long usuarioId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM RefreshToken r WHERE r.expiraEm < :limite")
    int apagarExpirados(@Param("limite") LocalDateTime limite);
}
