package com.glowup.api.repository;

import com.glowup.api.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {
    
    boolean existsByEmail(String email);
    
    Optional<Usuario> findByEmail(String email);
    
}