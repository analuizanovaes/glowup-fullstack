package com.glowup.api.repository;

import com.glowup.api.domain.Carrinho;
import com.glowup.api.domain.StatusCarrinho;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CarrinhoRepository extends JpaRepository<Carrinho, Long> {

    Optional<Carrinho> findByUsuarioIdAndStatus(Long usuarioId, StatusCarrinho status);
}
