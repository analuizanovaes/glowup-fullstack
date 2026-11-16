package com.glowup.api.repository;

import com.glowup.api.domain.PedidoEndereco;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PedidoEnderecoRepository extends JpaRepository<PedidoEndereco, Long> {
    java.util.Optional<PedidoEndereco> findByPedidoId(Long pedidoId);
}
