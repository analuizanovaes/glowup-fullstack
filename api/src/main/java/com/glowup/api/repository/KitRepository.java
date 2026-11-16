package com.glowup.api.repository;

import com.glowup.api.domain.Kit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface KitRepository extends JpaRepository<Kit, Long> {
    @Query(value = """
            SELECT k FROM Kit k
            JOIN FETCH k.categoria
            LEFT JOIN FETCH k.estoque
            WHERE k.ativo = true
              AND (cast(:categoria as String) IS NULL OR cast(function('glowup_unaccent', lower(k.categoria.nome)) as String) = cast(function('glowup_unaccent', lower(cast(:categoria as String))) as String))
            """,
            countQuery = """
            SELECT count(k) FROM Kit k
            WHERE k.ativo = true
              AND (cast(:categoria as String) IS NULL OR cast(function('glowup_unaccent', lower(k.categoria.nome)) as String) = cast(function('glowup_unaccent', lower(cast(:categoria as String))) as String))
            """)
    Page<Kit> buscarComFiltrosPaginados(@Param("categoria") String categoria, Pageable pageable);

    @Query(value = "SELECT produto_componente_id AS \"produtoId\", quantidade AS \"quantidade\" FROM kit_itens WHERE produto_kit_id = :kitId", nativeQuery = true)
    List<ComponenteKitEstoqueProjecao> buscarComponentesParaEstorno(@Param("kitId") Long kitId);
}

