package com.glowup.api.repository;

import com.glowup.api.domain.NotificacaoAdmin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificacaoAdminRepository extends JpaRepository<NotificacaoAdmin, Long> {

    List<NotificacaoAdmin> findByVisualizadaFalseOrderByCriadaEmDesc();
}
