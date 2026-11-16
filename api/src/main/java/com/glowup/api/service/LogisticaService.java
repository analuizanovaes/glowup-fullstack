package com.glowup.api.service;

import com.glowup.api.domain.ItemPedido;
import com.glowup.api.domain.Produto;
import com.glowup.api.dto.ConsultaCepRespostaDTO;
import com.glowup.api.dto.ViaCepResponseDTO;
import com.glowup.api.exception.RecursoNaoEncontradoException;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.text.Normalizer;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Regras logísticas (RN03 / RNF01): frete fixo de R$ 10,00 somente para Itapoã e Paranoá, bloqueio geográfico
 * em cascata (país, UF e bairro), prazo unificado e consulta de CEP com contingência (RNF03).
 */
@Service
public class LogisticaService {

    private static final BigDecimal FRETE_FIXO = new BigDecimal("10.00");
    private static final int PRAZO_PADRAO_DIAS = 7;
    private static final String MENSAGEM_RETIRADA = "Selecione 'Retirada Grátis'.";

    /** Bairros e condomínios atendidos pelo frete fixo (já sem acento, em maiúsculas), além de Itapoã e Paranoá. */
    private static final Set<String> BAIRROS_ATENDIDOS = Set.of(
            "ITAPOA", "PARANOA",
            "ITAPOA I", "ITAPOA II",
            "DEL LAGO", "DEL LAGO II",
            "BAIXINHO",
            "CONDOMINIO ITAPOA I", "CONDOMINIO ITAPOA II");

    /** Palavras (sem acento) que liberam o frete: Paranoá, Itapoã e as grafias Itapuã / Itapoá. */
    private static final Pattern PALAVRA_LOCALIDADE_ATENDIDA = Pattern.compile("\\b(PARANOA|ITAPOA|ITAPUA)\\b");
    private static final Pattern PALAVRA_PARQUE = Pattern.compile("\\bPARQUE\\b");

    private final RestClient clienteHttp;

    public LogisticaService() {
        // Timeout curto: uma queda do ViaCEP não pode travar o checkout (seção 6.18).
        HttpClient clienteJdk = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(clienteJdk);
        fabrica.setReadTimeout(Duration.ofSeconds(3));
        this.clienteHttp = RestClient.builder().requestFactory(fabrica).build();
    }

    /**
     * Cascata de bloqueios: (1) país diferente de Brasil, (2) UF diferente de DF, (3) bairro fora de
     * Itapoã/Paranoá. Os bairros "Parque" e quaisquer outros ficam restritos à retirada presencial.
     */
    public BigDecimal calcularFrete(String pais, String uf, String bairro) {
        String paisNormalizado = normalizar(pais);
        if (!(paisNormalizado.equals("BR") || paisNormalizado.equals("BRASIL")) || !"DF".equals(normalizar(uf))) {
            throw new IllegalArgumentException("Entregas limitadas ao Distrito Federal, Brasil.");
        }

        String bairroFormatado = normalizar(bairro);

        // Ignora pontuação (ex.: "Itapoã - II") antes de comparar.
        String chaveBairro = bairroFormatado.replaceAll("[^A-Z0-9 ]", " ").replaceAll("\\s+", " ").trim();

        // "Parque" (Paranoá Parque, Itapoã/Itapuã/Itapoá Parque) só tem retirada presencial.
        if (PALAVRA_PARQUE.matcher(chaveBairro).find() && PALAVRA_LOCALIDADE_ATENDIDA.matcher(chaveBairro).find()) {
            throw new IllegalArgumentException("Frete domiciliar indisponível para esta localidade. Selecione 'Retirada Grátis'.");
        }

        // Aceita qualquer endereço que contenha Paranoá ou Itapoã (e as grafias Itapuã/Itapoá),
        // com ou sem acento e em qualquer caixa, desde que não seja "Parque".
        if (PALAVRA_LOCALIDADE_ATENDIDA.matcher(chaveBairro).find() || BAIRROS_ATENDIDOS.contains(chaveBairro)) {
            return FRETE_FIXO;
        }

        throw new IllegalArgumentException("Região não atendida pelo frete fixo atual.");
    }

    public ViaCepResponseDTO buscarCepComFallback(String cep) {
        String cepLimpo = cep == null ? "" : cep.replaceAll("\\D", "");
        if (cepLimpo.length() != 8) {
            throw new IllegalArgumentException("CEP com formato inválido.");
        }
        try {
            return clienteHttp.get()
                    .uri("https://viacep.com.br/ws/" + cepLimpo + "/json/")
                    .retrieve()
                    .body(ViaCepResponseDTO.class);
        } catch (RestClientException excecao) {
            ViaCepResponseDTO fallback = new ViaCepResponseDTO();
            fallback.setErroConexao(true);
            return fallback;
        }
    }

    /**
     * Consulta o CEP e já informa se há entrega domiciliar. Em falha/timeout do ViaCEP ou rua vazia,
     * libera a digitação manual do endereço (o bloqueio logístico continua valendo no checkout).
     */
    public ConsultaCepRespostaDTO consultarCep(String cep) {
        String cepLimpo = cep == null ? "" : cep.replaceAll("\\D", "");
        ViaCepResponseDTO resposta = buscarCepComFallback(cepLimpo);

        if (resposta == null || resposta.isErroConexao()) {
            return new ConsultaCepRespostaDTO(cepLimpo, null, null, null, null, false, null, PRAZO_PADRAO_DIAS,
                    true, true, "Serviço de CEP indisponível no momento. Preencha o endereço manualmente.");
        }
        if (Boolean.TRUE.equals(resposta.getErro())) {
            throw new RecursoNaoEncontradoException("CEP não encontrado.");
        }

        boolean ruaVazia = resposta.getLogradouro() == null || resposta.getLogradouro().isBlank();

        BigDecimal frete = null;
        String mensagem = null;
        try {
            frete = calcularFrete("BR", resposta.getUf(), resposta.getBairro());
        } catch (IllegalArgumentException excecao) {
            mensagem = excecao.getMessage().contains("Retirada") ? excecao.getMessage()
                    : excecao.getMessage() + " " + MENSAGEM_RETIRADA;
        }
        if (ruaVazia && mensagem == null) {
            mensagem = "CEP sem logradouro: complete o endereço manualmente.";
        }

        return new ConsultaCepRespostaDTO(cepLimpo, resposta.getLogradouro(), resposta.getBairro(),
                resposta.getLocalidade(), resposta.getUf(), frete != null, frete, PRAZO_PADRAO_DIAS,
                ruaVazia, false, mensagem);
    }

    public Integer calcularPrazoUnificado(List<ItemPedido> itens) {
        if (itens == null || itens.isEmpty()) {
            return 0;
        }

        int prazoMaximo = 0;

        for (ItemPedido item : itens) {
            Produto produto = item.getProduto();

            int prazoProduto = produto.getPrazoEntregaDias() != null ? produto.getPrazoEntregaDias() : 0;

            if (prazoProduto > prazoMaximo) {
                prazoMaximo = prazoProduto;
            }
        }

        return prazoMaximo;
    }

    private static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String semAcento = Normalizer.normalize(texto.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcento.toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
