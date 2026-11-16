package com.glowup.api.repository;

import com.glowup.api.domain.Produto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ProdutoRepository extends JpaRepository<Produto, Long> {

    @Query("SELECT p FROM Produto p WHERE TYPE(p) = Produto AND p.ativo = true " +
           "AND (cast(:categoria as String) IS NULL OR LOWER(p.categoria.nome) = LOWER(cast(:categoria as String))) " +
           "AND (cast(:nome as String) IS NULL OR LOWER(p.nome) LIKE LOWER(CONCAT('%', cast(:nome as String), '%')))")
    List<Produto> buscarComFiltros(@Param("categoria") String categoria, @Param("nome") String nome);

    @Query(value = """
            SELECT p FROM Produto p
            JOIN FETCH p.categoria
            LEFT JOIN FETCH p.estoque
            WHERE TYPE(p) = Produto
              AND p.ativo = true
              AND (cast(:categoria as String) IS NULL OR cast(function('glowup_unaccent', lower(p.categoria.nome)) as String) = cast(function('glowup_unaccent', lower(cast(:categoria as String))) as String))
              AND (cast(:nome as String) IS NULL OR cast(function('glowup_unaccent', lower(p.nome)) as String) LIKE concat('%', cast(function('glowup_unaccent', lower(cast(:nome as String))) as String), '%') ESCAPE '!')
            """,
            countQuery = """
            SELECT count(p) FROM Produto p
            WHERE TYPE(p) = Produto
              AND p.ativo = true
              AND (cast(:categoria as String) IS NULL OR cast(function('glowup_unaccent', lower(p.categoria.nome)) as String) = cast(function('glowup_unaccent', lower(cast(:categoria as String))) as String))
              AND (cast(:nome as String) IS NULL OR cast(function('glowup_unaccent', lower(p.nome)) as String) LIKE concat('%', cast(function('glowup_unaccent', lower(cast(:nome as String))) as String), '%') ESCAPE '!')
            """)
    Page<Produto> buscarComFiltrosPaginados(@Param("categoria") String categoria,
                                            @Param("nome") String nome,
                                            Pageable pageable);

    @Query("SELECT p FROM Produto p WHERE p.id = :id AND TYPE(p) = Produto")
    Optional<Produto> buscarProdutoSimplesPorId(@Param("id") Long id);

    boolean existsByCategoriaId(Long categoriaId);

    /** Consulta nativa de propósito: o filtro de exclusão lógica da entidade esconderia os produtos inativos. */
    @Query(value = """
            SELECT p.id AS "id", p.nome AS "nome", p.descricao AS "descricao", p.marca AS "marca", c.nome AS "categoria",
                   p.tipo_kit AS "tipoKit", p.modalidade AS "modalidade", p.custo_fabrica AS "custoFabrica",
                   p.preco_base AS "precoBase", p.preco_promocional AS "precoPromocional",
                   p.data_fim_promocao AS "dataLimitePromocao",
                   p.imagem_url AS "imagemUrl", p.ativo AS "ativo",
                   e.quantidade AS "quantidade", e.quantidade_reservada AS "quantidadeReservada",
                   e.data_validade AS "dataValidade", p.tempo_medio_uso_dias AS "tempoMedioUsoDias"
            FROM produtos p
            JOIN categorias c ON c.id = p.categoria_id
            LEFT JOIN estoques e ON e.produto_id = p.id
            """,
            countQuery = "SELECT COUNT(*) FROM produtos p JOIN categorias c ON c.id = p.categoria_id",
            nativeQuery = true)
    Page<ProdutoAdminProjecao> listarTodosIncluindoInativos(Pageable pageable);

    /** Nativa de propósito: o filtro de exclusão lógica da entidade esconde os produtos inativos. */
    @Query(value = "SELECT COUNT(*) FROM produtos WHERE id = :id AND ativo = false", nativeQuery = true)
    long contarInativosPorId(@Param("id") Long id);

    /** Desfaz a exclusão lógica (produto ou kit). Só reativa se a categoria do produto estiver ativa; devolve as linhas alteradas. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE produtos SET ativo = true, deleted_at = NULL, updated_at = CURRENT_TIMESTAMP
            WHERE id = :id AND ativo = false
              AND EXISTS (SELECT 1 FROM categorias c WHERE c.id = produtos.categoria_id AND c.ativo = true)
            """, nativeQuery = true)
    int reativarPorId(@Param("id") Long id);

    /** RN08: promoções cuja data limite já passou (a rotina da meia-noite as reverte ao preço base). */
    List<Produto> findByPrecoPromocionalIsNotNullAndDataLimitePromocaoBefore(LocalDateTime limite);
}


