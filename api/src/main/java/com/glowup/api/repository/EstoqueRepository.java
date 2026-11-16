package com.glowup.api.repository;

import com.glowup.api.domain.Estoque;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;

public interface EstoqueRepository extends JpaRepository<Estoque, Long> {

    @Query("SELECT e FROM Estoque e JOIN FETCH e.produto p WHERE p.id = :produtoId")
    Optional<Estoque> buscarPorProduto(@Param("produtoId") Long produtoId);

    /** Serializa contagem/entrada administrativa com as baixas atômicas do checkout. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Estoque e WHERE e.produto.id = :produtoId")
    Optional<Estoque> buscarPorProdutoParaAtualizacao(@Param("produtoId") Long produtoId);

    /** O JOIN exclui estoques de produtos inativados. */
    @Query("SELECT e FROM Estoque e JOIN FETCH e.produto p WHERE e.dataValidade IS NOT NULL")
    List<Estoque> buscarComValidade();

    @Query("SELECT e FROM Estoque e JOIN FETCH e.produto p ORDER BY p.nome")
    List<Estoque> listarTodos();

    /**
     * Baixa atômica (RN04 / RNF02): só debita se houver saldo livre. Retorna 0 linhas quando o estoque é
     * insuficiente, o que torna a corrida entre duas clientes segura sem depender de leitura prévia.
     */
    @Modifying
    @Query("UPDATE Estoque e SET e.quantidade = e.quantidade - :quantidade, e.atualizadoEm = :agora "
         + "WHERE e.produto.id = :produtoId AND (e.quantidade - e.quantidadeReservada) >= :quantidade")
    int baixarSaldo(@Param("produtoId") Long produtoId,
                    @Param("quantidade") int quantidade,
                    @Param("agora") LocalDateTime agora);

    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE estoques SET quantidade = quantidade + :quantidade, atualizado_at = :agora WHERE produto_id = :produtoId", nativeQuery = true)
    int adicionarSaldo(@Param("produtoId") Long produtoId, @Param("quantidade") int quantidade,
                       @Param("agora") LocalDateTime agora);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Estoque e SET e.quantidade = e.quantidade + :quantidade, e.atualizadoEm = :agora WHERE e.produto.id = :produtoId")
    int adicionarSaldoAdmin(@Param("produtoId") Long produtoId, @Param("quantidade") int quantidade,
                            @Param("agora") LocalDateTime agora);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Estoque e SET e.quantidade = :quantidade, e.atualizadoEm = :agora WHERE e.produto.id = :produtoId")
    int definirSaldo(@Param("produtoId") Long produtoId, @Param("quantidade") int quantidade,
                     @Param("agora") LocalDateTime agora);

    @Query(value = "SELECT id FROM estoques WHERE produto_id = :produtoId", nativeQuery = true)
    Optional<Long> buscarIdPorProduto(@Param("produtoId") Long produtoId);
}
