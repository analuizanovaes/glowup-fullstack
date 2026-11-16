package com.glowup.api.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Valida em PostgreSQL real as consultas HQL com {@code glowup_unaccent}. A validação só na inicialização do
 * repositório não garante que o SQL gerado execute; estes testes cobrem o caminho completo.
 */
@SpringBootTest
@ActiveProfiles("test")
class BuscaCatalogoRepositoryTest {

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private KitRepository kitRepository;

    @Test
    void buscaDeProdutosSemFiltrosExecuta() {
        var pagina = produtoRepository.buscarComFiltrosPaginados(null, null, PageRequest.of(0, 5));

        assertFalse(pagina.isEmpty());
    }

    @Test
    void buscaDeProdutosComCategoriaENomeSemAcentoExecuta() {
        assertDoesNotThrow(() -> produtoRepository.buscarComFiltrosPaginados(
                "maquiagem", "lily", PageRequest.of(0, 5)));
    }

    @Test
    void buscaDeKitsComCategoriaSemAcentoExecuta() {
        assertDoesNotThrow(() -> kitRepository.buscarComFiltrosPaginados(
                "maquiagem", PageRequest.of(0, 5)));
    }
}
