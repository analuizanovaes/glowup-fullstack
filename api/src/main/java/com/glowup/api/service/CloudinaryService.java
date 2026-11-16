package com.glowup.api.service;

import com.glowup.api.exception.ArmazenamentoImagemException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;

/**
 * Envia imagens ao Cloudinary por upload assinado (API REST). O PostgreSQL guarda apenas a URL devolvida;
 * o arquivo nunca é persistido no servidor nem no banco.
 */
@Service
@Slf4j
public class CloudinaryService {

    private static final String URL_BASE = "https://api.cloudinary.com/v1_1/";
    private static final ParameterizedTypeReference<Map<String, Object>> TIPO_MAPA = new ParameterizedTypeReference<>() { };
    private static final long TAMANHO_MAXIMO_BYTES = 5L * 1024 * 1024;

    private final RestClient clienteHttp;
    private final String nomeDaConta;
    private final String chaveApi;
    private final String segredoApi;

    public CloudinaryService(@Value("${cloudinary.cloud-name:}") String nomeDaConta,
                             @Value("${cloudinary.api-key:}") String chaveApi,
                             @Value("${cloudinary.api-secret:}") String segredoApi) {
        this.nomeDaConta = nomeDaConta;
        this.chaveApi = chaveApi;
        this.segredoApi = segredoApi;

        HttpClient clienteJdk = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory fabricaRequisicoes = new JdkClientHttpRequestFactory(clienteJdk);
        fabricaRequisicoes.setReadTimeout(Duration.ofSeconds(20));
        this.clienteHttp = RestClient.builder().requestFactory(fabricaRequisicoes).build();
    }

    /** Retorna a URL pública (HTTPS) da imagem enviada para a pasta informada. */
    public String enviarImagem(MultipartFile arquivo, String pasta) {
        byte[] conteudo = lerEValidar(arquivo);
        exigirCredenciais();

        long instante = System.currentTimeMillis() / 1000;
        MultiValueMap<String, Object> corpo = new LinkedMultiValueMap<>();
        corpo.add("file", recursoDaImagem(conteudo));
        corpo.add("api_key", chaveApi);
        corpo.add("timestamp", String.valueOf(instante));
        corpo.add("folder", pasta);
        corpo.add("signature", assinar(pasta, instante));

        try {
            Map<String, Object> resposta = clienteHttp.post()
                    .uri(URL_BASE + nomeDaConta + "/image/upload")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(corpo)
                    .retrieve()
                    .body(TIPO_MAPA);
            return extrairUrl(resposta);
        } catch (RestClientException excecao) {
            log.error("Falha no upload ao Cloudinary: {}", excecao.getMessage());
            throw new ArmazenamentoImagemException("Não foi possível enviar a imagem agora. Tente novamente em instantes.");
        }
    }

    byte[] lerEValidar(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new IllegalArgumentException("Envie um arquivo de imagem no campo 'arquivo'.");
        }
        if (arquivo.getSize() > TAMANHO_MAXIMO_BYTES) {
            throw new IllegalArgumentException("A imagem deve ter no máximo 5 MB.");
        }
        byte[] conteudo = lerBytes(arquivo);
        if (extensaoPelaAssinatura(conteudo) == null) {
            throw new IllegalArgumentException("Formato de imagem não aceito. Envie JPEG, PNG ou WebP.");
        }
        return conteudo;
    }

    // O tipo é identificado pelos primeiros bytes: o Content-Type enviado pelo cliente pode ser forjado.
    static String extensaoPelaAssinatura(byte[] conteudo) {
        if (conteudo.length >= 3 && (conteudo[0] & 0xFF) == 0xFF && (conteudo[1] & 0xFF) == 0xD8 && (conteudo[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (conteudo.length >= 8 && (conteudo[0] & 0xFF) == 0x89 && conteudo[1] == 'P' && conteudo[2] == 'N' && conteudo[3] == 'G') {
            return "png";
        }
        if (conteudo.length >= 12 && conteudo[0] == 'R' && conteudo[1] == 'I' && conteudo[2] == 'F' && conteudo[3] == 'F'
                && conteudo[8] == 'W' && conteudo[9] == 'E' && conteudo[10] == 'B' && conteudo[11] == 'P') {
            return "webp";
        }
        return null;
    }

    // Assinatura do Cloudinary: SHA-1 dos parâmetros em ordem alfabética (sem file/api_key) + segredo da API.
    String assinar(String pasta, long instante) {
        String textoAssinado = "folder=" + pasta + "&timestamp=" + instante + segredoApi;
        try {
            byte[] resumo = MessageDigest.getInstance("SHA-1").digest(textoAssinado.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(resumo);
        } catch (NoSuchAlgorithmException excecao) {
            throw new IllegalStateException("SHA-1 indisponível na JVM.", excecao);
        }
    }

    private byte[] lerBytes(MultipartFile arquivo) {
        try {
            return arquivo.getBytes();
        } catch (IOException excecao) {
            throw new IllegalArgumentException("Não foi possível ler o arquivo enviado.");
        }
    }

    // Nome fixo e seguro: o nome original enviado pelo cliente nunca é repassado.
    private ByteArrayResource recursoDaImagem(byte[] conteudo) {
        String nomeSeguro = "produto." + extensaoPelaAssinatura(conteudo);
        return new ByteArrayResource(conteudo) {
            @Override
            public String getFilename() {
                return nomeSeguro;
            }
        };
    }

    private void exigirCredenciais() {
        if (nomeDaConta.isBlank() || chaveApi.isBlank() || segredoApi.isBlank()) {
            throw new ArmazenamentoImagemException("Armazenamento de imagens não configurado no servidor.");
        }
    }

    private String extrairUrl(Map<String, Object> resposta) {
        Object url = resposta == null ? null : resposta.get("secure_url");
        if (!(url instanceof String urlImagem) || urlImagem.isBlank()) {
            throw new ArmazenamentoImagemException("O serviço de imagens retornou uma resposta inesperada.");
        }
        return urlImagem;
    }
}
