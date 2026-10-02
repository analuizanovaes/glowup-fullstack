package com.glowup.api.repository;

import com.glowup.api.model.RecuperacaoSenha;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RecuperacaoSenhaRepository extends JpaRepository<RecuperacaoSenha, Long> {
    Optional<RecuperacaoSenha> findByUsuarioEmailAndCodigoOtpAndUtilizadoFalse(String email, String codigoOtp);
}