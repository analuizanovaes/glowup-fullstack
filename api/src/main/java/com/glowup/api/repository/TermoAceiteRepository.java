package com.glowup.api.repository;

import com.glowup.api.model.TermoAceite;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface TermoAceiteRepository extends JpaRepository<TermoAceite, UUID> {
}