package com.glowup.api.repository;

import com.glowup.api.domain.AnaliseReposicao;
import com.glowup.api.domain.ItemPedido;
import com.glowup.api.domain.StatusPedido;
import com.glowup.api.domain.StatusReposicao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface AnaliseReposicaoRepository extends JpaRepository<AnaliseReposicao, Long> {

    @Query("SELECT a FROM AnaliseReposicao a JOIN FETCH a.produto p JOIN FETCH a.usuario u "
         + "WHERE a.status = :status ORDER BY a.dataEstimadaReposicao ASC")
    List<AnaliseReposicao> listarPorStatus(@Param("status") StatusReposicao status);

    /** Itens de pedidos pagos, de produtos com tempo médio de uso, que ainda não geraram sugestão. */
    @Query("SELECT i FROM ItemPedido i JOIN FETCH i.pedido p JOIN FETCH p.cliente c JOIN FETCH i.produto pr "
         + "WHERE p.status IN :status AND pr.tempoMedioUsoDias IS NOT NULL "
         + "AND NOT EXISTS (SELECT a.id FROM AnaliseReposicao a WHERE a.pedidoId = p.id AND a.produto.id = pr.id)")
    List<ItemPedido> buscarItensSemAnalise(@Param("status") Collection<StatusPedido> status);
}
