package com.glowup.api.repository;

import com.glowup.api.domain.WebhookPagamento;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookPagamentoRepository extends JpaRepository<WebhookPagamento, Long> {

    boolean existsByEventoId(String eventoId);
}
