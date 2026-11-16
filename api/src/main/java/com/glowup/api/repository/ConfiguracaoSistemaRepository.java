package com.glowup.api.repository;

import com.glowup.api.model.ConfiguracaoSistema;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfiguracaoSistemaRepository extends JpaRepository<ConfiguracaoSistema, String> {
}