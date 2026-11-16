package com.glowup.api.service;

import com.glowup.api.dto.DadosPagamentoGatewayDTO;
import com.glowup.api.exception.GatewayIndisponivelException;
import com.glowup.api.exception.GatewayRecusouRequisicaoException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Cliente HTTP da API REST do Mercado Pago. Isola toda a comunicação externa e traduz falhas
 * de rede ou do gateway para exceções de domínio, sem expor detalhes internos ao cliente final.
 */
@Component
@Slf4j
public class MercadoPagoGateway {

    private static final ParameterizedTypeReference<Map<String, Object>> TIPO_MAPA =
            new ParameterizedTypeReference<>() {};
    private static final String CAMINHO_PAGAMENTOS = "/v1/payments";
    private static final String CABECALHO_IDEMPOTENCIA = "X-Idempotency-Key";
    private static final String MENSAGEM_INDISPONIVEL =
            "O serviço de pagamento está temporariamente indisponível. Seu carrinho foi mantido, tente novamente em instantes.";
    private static final int LIMITE_CORPO_NO_LOG = 500;

    private final RestClient clienteHttp;
    private final boolean credencialConfigurada;
    private final boolean mockEnabled;
    /** Simulação: Pix gerado e ainda não pago (id -> instante de expiração). Só usado com o mock ligado. */
    private final Map<String, Instant> mockPixPendentes = new ConcurrentHashMap<>();
    /** Simulação: Pix cancelado por expiração. */
    private final Map<String, Boolean> mockPixCancelados = new ConcurrentHashMap<>();

    public MercadoPagoGateway(
            @Value("${mercadopago.access-token:}") String tokenAcesso,
            @Value("${mercadopago.api-url:https://api.mercadopago.com}") String urlBase,
            @Value("${mercadopago.timeout-conexao-ms:5000}") long timeoutConexaoMs,
            @Value("${mercadopago.timeout-leitura-ms:10000}") long timeoutLeituraMs,
            @Value("${glowup.pagamento.mock-enabled:false}") boolean mockSolicitado,
            Environment ambiente) {

        HttpClient clienteJdk = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(timeoutConexaoMs))
                .build();
        JdkClientHttpRequestFactory fabricaRequisicoes = new JdkClientHttpRequestFactory(clienteJdk);
        fabricaRequisicoes.setReadTimeout(Duration.ofMillis(timeoutLeituraMs));

        this.credencialConfigurada = tokenAcesso != null && !tokenAcesso.isBlank();
        boolean ambienteLocal = ambiente.acceptsProfiles(Profiles.of("local", "test", "dev"));
        this.mockEnabled = ambienteLocal && (mockSolicitado || !this.credencialConfigurada);
        this.clienteHttp = RestClient.builder()
                .baseUrl(urlBase)
                .requestFactory(fabricaRequisicoes)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAcesso)
                .build();

        if (!this.credencialConfigurada && !this.mockEnabled) {
            log.warn("Variável MP_TEST_ACCESS_TOKEN não definida: chamadas ao Mercado Pago serão recusadas.");
        }
        if (this.mockEnabled) {
            log.warn("Gateway de pagamento em modo SIMULAÇÃO local: nenhuma cobrança real será feita.");
        }
    }

    public DadosPagamentoGatewayDTO criarPagamento(Map<String, Object> corpo, String chaveIdempotencia) {
        if (mockEnabled) {
            // Pix real nasce "pending" (mostra QR Code e espera o pagamento); só o cartão aprova na hora.
            if (corpo != null && "pix".equals(corpo.get("payment_method_id"))) {
                return pagamentoMockPixPendente(corpo);
            }
            return pagamentoMockAprovado(corpo);
        }
        exigirCredencial();
        Map<String, Object> resposta = executar("criar pagamento", () -> clienteHttp.post()
                .uri(CAMINHO_PAGAMENTOS)
                .header(CABECALHO_IDEMPOTENCIA, chaveIdempotencia)
                .contentType(MediaType.APPLICATION_JSON)
                .body(corpo)
                .retrieve()
                .body(TIPO_MAPA));
        return interpretar(resposta);
    }

    public DadosPagamentoGatewayDTO consultarPagamento(String idGateway) {
        if (mockEnabled) {
            return pagamentoMockConsulta(idGateway);
        }
        exigirCredencial();
        Map<String, Object> resposta = executar("consultar pagamento", () -> clienteHttp.get()
                .uri(CAMINHO_PAGAMENTOS + "/{id}", idGateway)
                .retrieve()
                .body(TIPO_MAPA));
        return interpretar(resposta);
    }

    public void cancelarPagamento(String idGateway) {
        if (mockEnabled) {
            if (idGateway != null && mockPixPendentes.remove(idGateway) != null) {
                mockPixCancelados.put(idGateway, Boolean.TRUE);
            }
            return;
        }
        exigirCredencial();
        executar("cancelar pagamento", () -> clienteHttp.put()
                .uri(CAMINHO_PAGAMENTOS + "/{id}", idGateway)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("status", "cancelled"))
                .retrieve()
                .body(TIPO_MAPA));
    }

    public String estornarPagamento(String idGateway, String chaveIdempotencia, BigDecimal valorDeEstorno) {
        if (mockEnabled) {
            return "mock-estorno-" + idGateway;
        }
        exigirCredencial();
        Map<String, Object> corpo = (valorDeEstorno != null && valorDeEstorno.compareTo(BigDecimal.ZERO) > 0)
                ? Map.of("amount", valorDeEstorno.setScale(2, RoundingMode.HALF_UP))
                : Collections.emptyMap();
                
        Map<String, Object> resposta = executar("estornar pagamento", () -> clienteHttp.post()
                .uri(CAMINHO_PAGAMENTOS + "/{id}/refunds", idGateway)
                .header(CABECALHO_IDEMPOTENCIA, chaveIdempotencia)
                .contentType(MediaType.APPLICATION_JSON)
                .body(corpo)
                .retrieve()
                .body(TIPO_MAPA));
                
        return resposta == null ? null : texto(resposta.get("id"));
    }

    private DadosPagamentoGatewayDTO pagamentoMockAprovado(Map<String, Object> corpo) {
        BigDecimal valor = decimal(corpo == null ? null : corpo.get("transaction_amount"));
        return new DadosPagamentoGatewayDTO(
                "mock-" + UUID.randomUUID(),
                "approved",
                "accredited",
                valor,
                valor,
                valor,
                corpo == null ? null : inteiro(corpo.get("installments")),
                null,
                null,
                null,
                corpo == null ? null : texto(corpo.get("external_reference")));
    }

    private DadosPagamentoGatewayDTO pagamentoMockPixPendente(Map<String, Object> corpo) {
        BigDecimal valor = decimal(corpo.get("transaction_amount"));
        String id = "mock-" + UUID.randomUUID();
        mockPixPendentes.put(id, expiracaoMock(corpo.get("date_of_expiration")));
        // Copia-e-cola de simulação (o app do banco não reconhece): o front desenha o QR Code a partir dele.
        String copiaECola = "00020126360014br.gov.bcb.pix0114GLOWUP-SIMULACAO5204000053039865802BR"
                + "5907GLOWUP6008BRASILIA62070503***6304" + id.substring(5, 9).toUpperCase();
        return new DadosPagamentoGatewayDTO(id, "pending", "pending_waiting_transfer",
                valor, null, null, null, copiaECola, null, null,
                texto(corpo.get("external_reference")));
    }

    /**
     * Na simulação, consultar o Pix equivale a "a cliente pagou" (botão Efetuar pagamento), exceto se ele já
     * expirou ou foi cancelado: aí continua pendente/cancelado, como no gateway de verdade.
     */
    private DadosPagamentoGatewayDTO pagamentoMockConsulta(String idGateway) {
        String status = "approved";
        String detalhe = "accredited";
        if (idGateway != null && mockPixCancelados.containsKey(idGateway)) {
            status = "cancelled";
            detalhe = "expired";
        } else if (idGateway != null && mockPixPendentes.containsKey(idGateway)) {
            if (Instant.now().isAfter(mockPixPendentes.get(idGateway))) {
                status = "pending";
                detalhe = "pending_waiting_transfer";
            } else {
                mockPixPendentes.remove(idGateway);
            }
        }
        return new DadosPagamentoGatewayDTO(idGateway, status, detalhe,
                null, null, null, null, null, null, null, null);
    }

    private Instant expiracaoMock(Object dataExpiracao) {
        try {
            return OffsetDateTime.parse(String.valueOf(dataExpiracao)).toInstant();
        } catch (RuntimeException excecao) {
            return Instant.now().plus(Duration.ofMinutes(30));
        }
    }

    private void exigirCredencial() {
        if (!credencialConfigurada) {
            log.error("Operação bloqueada: MP_TEST_ACCESS_TOKEN não configurado.");
            throw new GatewayIndisponivelException(MENSAGEM_INDISPONIVEL);
        }
    }

    private Map<String, Object> executar(String operacao, Supplier<Map<String, Object>> chamada) {
        try {
            return chamada.get();
        } catch (HttpClientErrorException excecao) {
            throw traduzirErroDoGateway(operacao, excecao);
        } catch (RestClientException excecao) {
            log.error("Falha de comunicação com o Mercado Pago na operação '{}': {}", operacao, excecao.getMessage());
            throw new GatewayIndisponivelException(MENSAGEM_INDISPONIVEL);
        }
    }

    private RuntimeException traduzirErroDoGateway(String operacao, HttpClientErrorException excecao) {
        int status = excecao.getStatusCode().value();
        String corpo = resumir(excecao.getResponseBodyAsString());

        // 401/403 indicam credencial inválida: é falha de configuração nossa, não da cliente.
        if (status == 401 || status == 403) {
            log.error("Mercado Pago rejeitou as credenciais na operação '{}' (HTTP {}): {}", operacao, status, corpo);
            return new GatewayIndisponivelException(MENSAGEM_INDISPONIVEL);
        }

        log.warn("Mercado Pago recusou a operação '{}' (HTTP {}): {}", operacao, status, corpo);
        return new GatewayRecusouRequisicaoException(
                "O gateway de pagamento recusou a requisição. Revise os dados informados e tente novamente.");
    }

    private DadosPagamentoGatewayDTO interpretar(Map<String, Object> resposta) {
        if (resposta == null) {
            throw new GatewayIndisponivelException(MENSAGEM_INDISPONIVEL);
        }

        Map<String, Object> detalhesTransacao = comoMapa(resposta.get("transaction_details"));
        Map<String, Object> dadosPix = comoMapa(comoMapa(resposta.get("point_of_interaction")).get("transaction_data"));

        return new DadosPagamentoGatewayDTO(
                texto(resposta.get("id")),
                texto(resposta.get("status")),
                texto(resposta.get("status_detail")),
                decimal(resposta.get("transaction_amount")),
                decimal(detalhesTransacao.get("total_paid_amount")),
                decimal(detalhesTransacao.get("net_received_amount")),
                inteiro(resposta.get("installments")),
                texto(dadosPix.get("qr_code")),
                texto(dadosPix.get("qr_code_base64")),
                texto(dadosPix.get("ticket_url")),
                texto(resposta.get("external_reference")));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> comoMapa(Object valor) {
        if (valor instanceof Map<?, ?>) {
            return (Map<String, Object>) valor;
        }
        return Collections.emptyMap();
    }

    private String texto(Object valor) {
        return valor == null ? null : String.valueOf(valor);
    }

    private BigDecimal decimal(Object valor) {
        if (valor == null) {
            return null;
        }
        try {
            return new BigDecimal(valor.toString()).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException excecao) {
            return null;
        }
    }

    private Integer inteiro(Object valor) {
        return valor instanceof Number numero ? numero.intValue() : null;
    }

    private String resumir(String corpo) {
        if (corpo == null || corpo.length() <= LIMITE_CORPO_NO_LOG) {
            return corpo;
        }
        return corpo.substring(0, LIMITE_CORPO_NO_LOG) + "...";
    }
}
