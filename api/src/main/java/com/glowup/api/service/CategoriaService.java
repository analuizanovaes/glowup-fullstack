package com.glowup.api.service;

import com.glowup.api.domain.Categoria;
import com.glowup.api.dto.CategoriaRequisicaoDTO;
import com.glowup.api.exception.ConflitoDeEstadoException;
import com.glowup.api.exception.RecursoNaoEncontradoException;
import com.glowup.api.repository.CategoriaRepository;
import com.glowup.api.repository.ProdutoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Categorias do catálogo (RF02 / RF07). A exclusão é lógica e só vale para categorias sem produtos ativos. */
@Service
@RequiredArgsConstructor
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;
    private final ProdutoRepository produtoRepository;

    @Transactional(readOnly = true)
    public List<Categoria> listar() {
        return categoriaRepository.findAllByOrderByNomeAsc();
    }

    @Transactional
    public Categoria criar(CategoriaRequisicaoDTO dto) {
        String nome = dto.nome().trim();
        if (categoriaRepository.existsByNomeIgnoreCase(nome)) {
            throw new IllegalArgumentException("Já existe uma categoria chamada " + nome + ".");
        }
        Categoria categoria = new Categoria();
        categoria.setNome(nome);
        return categoriaRepository.save(categoria);
    }

    @Transactional
    public Categoria renomear(Long id, CategoriaRequisicaoDTO dto) {
        Categoria categoria = buscarOuFalhar(id);
        String nome = dto.nome().trim();
        if (!nome.equalsIgnoreCase(categoria.getNome()) && categoriaRepository.existsByNomeIgnoreCase(nome)) {
            throw new IllegalArgumentException("Já existe uma categoria chamada " + nome + ".");
        }
        categoria.setNome(nome);
        return categoriaRepository.save(categoria);
    }

    @Transactional
    public void excluir(Long id) {
        Categoria categoria = buscarOuFalhar(id);
        if (produtoRepository.existsByCategoriaId(id)) {
            throw new ConflitoDeEstadoException("A categoria possui produtos ativos. Mova ou inative os produtos antes.");
        }
        categoriaRepository.delete(categoria);
    }

    private Categoria buscarOuFalhar(Long id) {
        return categoriaRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Categoria não encontrada."));
    }
}
