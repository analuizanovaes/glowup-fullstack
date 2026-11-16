package com.glowup.api.controller;

import com.glowup.api.dto.ImagemProdutoRespostaDTO;
import com.glowup.api.service.ProdutoImagemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/produtos")
@RequiredArgsConstructor
@Tag(name = "Catálogo - Imagens", description = "Upload da foto do produto (somente administradora)")
public class ProdutoImagemController {

    private final ProdutoImagemService produtoImagemService;

    @Operation(summary = "Envia a foto do produto",
            description = "Recebe JPEG, PNG ou WebP (até 5 MB) no campo 'arquivo', envia ao Cloudinary e grava apenas a URL no produto.")
    @PostMapping(path = "/{id}/imagem", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImagemProdutoRespostaDTO> enviarImagem(@PathVariable Long id,
                                                                  @RequestParam("arquivo") MultipartFile arquivo) {
        return ResponseEntity.ok(produtoImagemService.atualizarImagem(id, arquivo));
    }
}
