package com.glowup.api.service;

import com.glowup.api.domain.Produto;
import com.glowup.api.dto.ImagemProdutoRespostaDTO;
import com.glowup.api.exception.RecursoNaoEncontradoException;
import com.glowup.api.repository.ProdutoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ProdutoImagemService {

    private static final String PASTA_DAS_IMAGENS = "glowup/produtos";

    private final ProdutoRepository produtoRepository;
    private final CloudinaryService cloudinaryService;

    // Sem @Transactional de propósito: a chamada de rede ao Cloudinary não deve manter uma transação aberta.
    public ImagemProdutoRespostaDTO atualizarImagem(Long produtoId, MultipartFile arquivo) {
        Produto produto = produtoRepository.findById(produtoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto não encontrado."));

        String urlImagem = cloudinaryService.enviarImagem(arquivo, PASTA_DAS_IMAGENS);

        produto.setImagemUrl(urlImagem);
        produtoRepository.save(produto);
        return new ImagemProdutoRespostaDTO(produto.getId(), urlImagem);
    }
}
