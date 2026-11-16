package com.glowup.api.repository;

import com.glowup.api.model.Endereco;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EnderecoRepository extends JpaRepository<Endereco, Long> {

    List<Endereco> findByUsuarioIdOrderByPrincipalDescIdAsc(Long usuarioId);

    Optional<Endereco> findByIdAndUsuarioId(Long id, Long usuarioId);
}
