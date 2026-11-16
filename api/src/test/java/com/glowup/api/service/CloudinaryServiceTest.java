package com.glowup.api.service;

import com.glowup.api.exception.ArmazenamentoImagemException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CloudinaryServiceTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] WEBP = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};

    private final CloudinaryService cloudinaryService = new CloudinaryService("conta", "chave", "segredo");

    @Test
    @DisplayName("Identifica JPEG, PNG e WebP pelos primeiros bytes")
    void deveIdentificarFormatosPelaAssinatura() {
        assertEquals("jpg", CloudinaryService.extensaoPelaAssinatura(JPEG));
        assertEquals("png", CloudinaryService.extensaoPelaAssinatura(PNG));
        assertEquals("webp", CloudinaryService.extensaoPelaAssinatura(WEBP));
    }

    @Test
    @DisplayName("Não reconhece texto, scripts nem arquivos curtos demais como imagem")
    void deveRejeitarConteudoQueNaoEhImagem() {
        assertNull(CloudinaryService.extensaoPelaAssinatura("<?php echo 1; ?>".getBytes(StandardCharsets.UTF_8)));
        assertNull(CloudinaryService.extensaoPelaAssinatura(new byte[] {(byte) 0xFF}));
    }

    @Test
    @DisplayName("Aceita imagem válida e devolve seus bytes")
    void deveAceitarImagemValida() {
        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "foto.jpg", "image/jpeg", JPEG);

        assertArrayEquals(JPEG, cloudinaryService.lerEValidar(arquivo));
    }

    @Test
    @DisplayName("Recusa arquivo vazio, grande demais ou com Content-Type forjado")
    void deveRecusarArquivosInvalidos() {
        MockMultipartFile vazio = new MockMultipartFile("arquivo", "foto.jpg", "image/jpeg", new byte[0]);
        MockMultipartFile grande = new MockMultipartFile("arquivo", "foto.jpg", "image/jpeg", new byte[5 * 1024 * 1024 + 1]);
        MockMultipartFile forjado = new MockMultipartFile("arquivo", "foto.png", "image/png",
                "isto não é uma imagem".getBytes(StandardCharsets.UTF_8));

        assertEquals("Envie um arquivo de imagem no campo 'arquivo'.",
                assertThrows(IllegalArgumentException.class, () -> cloudinaryService.lerEValidar(vazio)).getMessage());
        assertEquals("A imagem deve ter no máximo 5 MB.",
                assertThrows(IllegalArgumentException.class, () -> cloudinaryService.lerEValidar(grande)).getMessage());
        assertEquals("Formato de imagem não aceito. Envie JPEG, PNG ou WebP.",
                assertThrows(IllegalArgumentException.class, () -> cloudinaryService.lerEValidar(forjado)).getMessage());
    }

    @Test
    @DisplayName("Assinatura do upload é o SHA-1 de 'folder=...&timestamp=...' + segredo")
    void deveAssinarOUploadComSha1() {
        assertEquals("9269395466be215200a20e37fce9d7c32a4907ee",
                cloudinaryService.assinar("glowup/produtos", 1700000000L));
    }

    @Test
    @DisplayName("Sem credenciais do Cloudinary, falha de forma controlada e sem acessar a rede")
    void deveFalharSemCredenciais() {
        CloudinaryService semCredenciais = new CloudinaryService("", "", "");
        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "foto.jpg", "image/jpeg", JPEG);

        ArmazenamentoImagemException excecao = assertThrows(ArmazenamentoImagemException.class,
                () -> semCredenciais.enviarImagem(arquivo, "glowup/produtos"));

        assertEquals("Armazenamento de imagens não configurado no servidor.", excecao.getMessage());
    }
}
