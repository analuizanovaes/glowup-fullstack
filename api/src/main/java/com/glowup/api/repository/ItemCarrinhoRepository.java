package com.glowup.api.repository;

import com.glowup.api.domain.ItemCarrinho;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ItemCarrinhoRepository extends JpaRepository<ItemCarrinho, Long> {

    /** O JOIN ignora itens cujo produto foi inativado (Soft Delete), evitando falha ao montar a sacola. */
    @Query("SELECT i FROM ItemCarrinho i JOIN FETCH i.produto p WHERE i.carrinhoId = :carrinhoId ORDER BY i.id")
    List<ItemCarrinho> listarItens(@Param("carrinhoId") Long carrinhoId);

    @Query("SELECT i FROM ItemCarrinho i JOIN FETCH i.produto p WHERE i.carrinhoId = :carrinhoId AND p.id = :produtoId")
    Optional<ItemCarrinho> buscarItem(@Param("carrinhoId") Long carrinhoId, @Param("produtoId") Long produtoId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM ItemCarrinho i WHERE i.carrinhoId = :carrinhoId AND i.produto.id = :produtoId")
    int removerItem(@Param("carrinhoId") Long carrinhoId, @Param("produtoId") Long produtoId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM ItemCarrinho i WHERE i.carrinhoId = :carrinhoId")
    void deleteByCarrinhoId(@Param("carrinhoId") Long carrinhoId);
}
