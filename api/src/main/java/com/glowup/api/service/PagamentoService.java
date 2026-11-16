package com.glowup.api.service;

import com.glowup.api.domain.MetodoPagamento;
import com.glowup.api.domain.Pagamento;
import com.glowup.api.domain.Pedido;
import com.glowup.api.domain.StatusPagamento;
import com.glowup.api.domain.StatusPedido;
import com.glowup.api.dto.DadosPagamentoGatewayDTO;
import com.glowup.api.dto.PagamentoRequisicaoDTO;
import com.glowup.api.dto.PagamentoRespostaDTO;
import com.glowup.api.exception.ConflitoDeEstadoException;
import com.glowup.api.exception.RecursoNaoEncontradoException;
import com.glowup.api.model.Usuario;
import com.glowup.api.repository.PagamentoRepository;
import com.glowup.api.repository.PedidoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Orquestra a criação do pagamento. A chamada ao gateway ocorre fora de transação: se ela falhar,
 * nada é gravado, o pedido permanece aguardando pagamento e o carrinho não é tocado (RN06).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PagamentoService {

    private static final int MAXIMO_PARCELAS = 3;
    private static final ZoneId FUSO_BRASILIA = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter FORMATO_DATA_GATEWAY =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    private final PedidoRepository pedidoRepository;
    private final PagamentoRepository pagamentoRepository;
    private final MercadoPagoGateway gateway;
    private final ConciliacaoPagamentoService conciliacaoService;
    private final CrmAdminService crmAdminService;

    @Value("${mercadopago.notification-url:}")
    private String urlNotificacao;

    @Value("${mercadopago.email-pagador-teste:}")
    private String emailPagadorTeste;

    @Value("${glowup.pagamento.pix-expiracao-minutos:30}")
    private long expiracaoPixMinutos;

    public PagamentoRespostaDTO iniciarPagamento(Usuario cliente, PagamentoRequisicaoDTO dto) {
        Pedido pedido = buscarPedidoDoCliente(cliente, dto.pedidoId());
        int parcelas = definirParcelas(dto);

        Optional<Pagamento> pagamentoExistente = pagamentoRepository.findByPedidoId(pedido.getId());
        if (pagamentoExistente.isPresent()) {
            return reaproveitarPagamento(pedido, pagamentoExistente.get(), dto.metodo());
        }
        if (pedido.getStatus() != StatusPedido.AGUARDANDO_PAGAMENTO) {
            throw new ConflitoDeEstadoException("O pedido não está aguardando pagamento.");
        }
        validarIntegridadeDoPedido(pedido);
        validarDadosDoMetodo(dto);

        ZonedDateTime expiracaoPix = ZonedDateTime.now(FUSO_BRASILIA).plusMinutes(expiracaoPixMinutos);
        Map<String, Object> corpo = montarCorpoDoPagamento(pedido, cliente, dto, parcelas, expiracaoPix);
        DadosPagamentoGatewayDTO dados = gateway.criarPagamento(corpo, gerarChaveIdempotencia(pedido, dto, parcelas));

        try {
            return conciliacaoService.registrarNovoPagamento(
                    pedido.getId(), dto.metodo(), parcelas, expiracaoPix.toLocalDateTime(), dados);
        } catch (RuntimeException falhaAoRegistrar) {
            boolean mesmoPagamentoJaRegistrado;
            Optional<Pagamento> pagamentoConcorrente;
            try {
                pagamentoConcorrente = pagamentoRepository.findByPedidoId(pedido.getId());
                mesmoPagamentoJaRegistrado = pagamentoConcorrente
                        .map(p -> dados.id() != null && dados.id().equals(p.getIdGateway())).orElse(false);
            } catch (RuntimeException falhaAoConferir) {
                log.error("Não foi possível conferir o pagamento {} já associado ao pedido {}.",
                        dados.id(), pedido.getId(), falhaAoConferir);
                notificarConciliacao(pedido.getId(), dados.id(), "não foi possível confirmar o registro local");
                throw falhaAoRegistrar;
            }
            if (mesmoPagamentoJaRegistrado) {
                log.info("Pagamento {} já está registrado para o pedido {}; ignorando a falha concorrente sem cancelar.",
                        dados.id(), pedido.getId());
                return PagamentoRespostaDTO.de(pagamentoConcorrente.orElseThrow(), pedido.getId(),
                        pedido.getStatus(), dados.pixQrCodeBase64());
            }
            try {
                gateway.cancelarPagamento(dados.id());
            } catch (RuntimeException falhaCancelamento) {
                // Pagamento já aprovado (cartão) não pode ser cancelado: a compensação correta é o estorno.
                try {
                    gateway.estornarPagamento(dados.id(), "compensacao-" + dados.id(), null);
                } catch (RuntimeException falhaCompensacao) {
                    log.error("Falha ao compensar pagamento {} após erro ao registrar pedido {}. Exige conciliação.",
                            dados.id(), pedido.getId(), falhaCompensacao);
                    notificarConciliacao(pedido.getId(), dados.id(), "não pôde ser compensado automaticamente");
                }
            }
            throw falhaAoRegistrar;
        }
    }

    private void notificarConciliacao(Long pedidoId, String idGateway, String motivo) {
        crmAdminService.criarNotificacao("CONCILIACAO_PAGAMENTO", "Pagamento exige conciliação",
                "O pagamento " + idGateway + " do pedido #" + pedidoId + " " + motivo + ".", pedidoId);
    }

    public PagamentoRespostaDTO consultarPorPedido(Usuario cliente, Long pedidoId) {
        Pedido pedido = buscarPedidoDoCliente(cliente, pedidoId);
        Pagamento pagamento = buscarPagamentoDoPedido(pedido.getId());
        return PagamentoRespostaDTO.de(pagamento, pedido.getId(), pedido.getStatus(), null);
    }

    public PagamentoRespostaDTO sincronizarComGateway(Usuario cliente, Long pedidoId) {
        Pedido pedido = buscarPedidoDoCliente(cliente, pedidoId);
        Pagamento pagamento = buscarPagamentoDoPedido(pedido.getId());

        DadosPagamentoGatewayDTO dados = gateway.consultarPagamento(pagamento.getIdGateway());
        conciliacaoService.aplicarResultado(pedido.getId(), dados, "CLIENTE:SINCRONIZACAO");
        return consultarPorPedido(cliente, pedidoId);
    }

    public void sincronizarPorIdDoGateway(Long pedidoId, String idGateway, String origem) {
        DadosPagamentoGatewayDTO dados = gateway.consultarPagamento(idGateway);
        conciliacaoService.aplicarResultado(pedidoId, dados, origem);
    }

    public void encerrarPixExpirado(Long pedidoId) {
        Pagamento pagamento = pagamentoRepository.findByPedidoId(pedidoId).orElse(null);
        if (pagamento == null || pagamento.getIdGateway() == null) {
            return;
        }

        DadosPagamentoGatewayDTO dados = gateway.consultarPagamento(pagamento.getIdGateway());
        if ("pending".equals(dados.status())) {
            gateway.cancelarPagamento(pagamento.getIdGateway());
            dados = gateway.consultarPagamento(pagamento.getIdGateway());
        }
        conciliacaoService.aplicarResultado(pedidoId, dados, "SISTEMA:PIX_EXPIRADO");
    }

    /**
     * O valor cobrado é o total do pedido; antes de cobrar, confere se ele fecha com os itens congelados
     * (soma dos itens - desconto + frete). Impede cobrar um total adulterado na criação do pedido.
     */
    private void validarIntegridadeDoPedido(Pedido pedido) {
        BigDecimal somaItens = pedidoRepository.somarItens(pedido.getId());
        if (somaItens == null || !valoresFecham(pedido, somaItens)) {
            log.error("Pedido {} com valores inconsistentes: pagamento bloqueado.", pedido.getId());
            throw new ConflitoDeEstadoException("Os valores do pedido são inconsistentes. Refaça o pedido.");
        }
    }

    private boolean valoresFecham(Pedido pedido, BigDecimal somaItens) {
        BigDecimal totalEsperado = somaItens.subtract(pedido.getDescontoCupom()).add(pedido.getValorFrete());
        return somaItens.compareTo(pedido.getSubtotal()) == 0
                && totalEsperado.compareTo(pedido.getValorTotal()) == 0;
    }

    private Pedido buscarPedidoDoCliente(Usuario cliente, Long pedidoId) {
        // Pedidos de outras clientes respondem como inexistentes para não revelar sua existência.
        return pedidoRepository.findByIdAndClienteId(pedidoId, cliente.getId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido não encontrado."));
    }

    private Pagamento buscarPagamentoDoPedido(Long pedidoId) {
        return pagamentoRepository.findByPedidoId(pedidoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Este pedido ainda não possui pagamento."));
    }

    private int definirParcelas(PagamentoRequisicaoDTO dto) {
        if (dto.metodo() == MetodoPagamento.PIX || dto.parcelas() == null) {
            return 1;
        }
        if (dto.parcelas() < 1 || dto.parcelas() > MAXIMO_PARCELAS) {
            throw new IllegalArgumentException("O parcelamento máximo permitido é de " + MAXIMO_PARCELAS + "x.");
        }
        return dto.parcelas();
    }

    private PagamentoRespostaDTO reaproveitarPagamento(Pedido pedido, Pagamento pagamento, MetodoPagamento metodoSolicitado) {
        boolean mesmoMetodo = pagamento.getMetodo() == metodoSolicitado;
        boolean pixPendente = pagamento.getMetodo() == MetodoPagamento.PIX
                && pagamento.getStatus() == StatusPagamento.PENDENTE;
        if (!mesmoMetodo || !pixPendente) {
            throw new ConflitoDeEstadoException("Este pedido já possui um pagamento registrado ("
                    + pagamento.getMetodo() + ", " + pagamento.getStatus() + ").");
        }
        return PagamentoRespostaDTO.de(pagamento, pedido.getId(), pedido.getStatus(), null);
    }

    private void validarDadosDoMetodo(PagamentoRequisicaoDTO dto) {
        if (dto.metodo() != MetodoPagamento.CARTAO) {
            return;
        }
        boolean tokenAusente = dto.tokenCartao() == null || dto.tokenCartao().isBlank();
        boolean bandeiraAusente = dto.bandeiraCartao() == null || dto.bandeiraCartao().isBlank();
        boolean cpfAusente = dto.documentoPagador() == null || dto.documentoPagador().isBlank();
        if (tokenAusente || bandeiraAusente || cpfAusente) {
            throw new IllegalArgumentException("Para pagamento com cartão informe tokenCartao, bandeiraCartao e documentoPagador (CPF).");
        }
        if (!cpfValido(dto.documentoPagador())) {
            throw new IllegalArgumentException("O CPF do pagador é inválido.");
        }
    }

    private boolean cpfValido(String cpf) {
        if (cpf == null || !cpf.matches("\\d{11}") || cpf.chars().distinct().count() == 1) return false;
        int soma = 0;
        for (int i = 0; i < 9; i++) soma += (cpf.charAt(i) - '0') * (10 - i);
        int primeiro = (soma * 10) % 11;
        if (primeiro == 10) primeiro = 0;
        if (primeiro != cpf.charAt(9) - '0') return false;
        soma = 0;
        for (int i = 0; i < 10; i++) soma += (cpf.charAt(i) - '0') * (11 - i);
        int segundo = (soma * 10) % 11;
        if (segundo == 10) segundo = 0;
        return segundo == cpf.charAt(10) - '0';
    }

    private String gerarChaveIdempotencia(Pedido pedido, PagamentoRequisicaoDTO dto, int parcelas) {
        // Chave estável: uma nova tentativa após timeout não gera cobrança duplicada no gateway.
        return "glowup-pagamento-" + pedido.getId() + "-" + dto.metodo() + "-" + parcelas;
    }

    private Map<String, Object> montarCorpoDoPagamento(Pedido pedido, Usuario cliente, PagamentoRequisicaoDTO dto,
                                                       int parcelas, ZonedDateTime expiracaoPix) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("transaction_amount", pedido.getValorTotal().setScale(2, RoundingMode.HALF_UP));
        corpo.put("description", "Pedido GlowUp #" + pedido.getId());
        corpo.put("external_reference", String.valueOf(pedido.getId()));
        corpo.put("payer", montarPagador(cliente, dto.documentoPagador()));

        if (urlNotificacao != null && !urlNotificacao.isBlank()) {
            corpo.put("notification_url", urlNotificacao);
        }
        if (dto.metodo() == MetodoPagamento.PIX) {
            corpo.put("payment_method_id", "pix");
            corpo.put("date_of_expiration", expiracaoPix.format(FORMATO_DATA_GATEWAY));
        } else {
            corpo.put("token", dto.tokenCartao());
            corpo.put("payment_method_id", dto.bandeiraCartao());
            corpo.put("installments", parcelas);
            corpo.put("statement_descriptor", "GLOWUP");
            // Modo binário: o gateway responde apenas aprovado ou rejeitado, sem análise manual pendente.
            corpo.put("binary_mode", true);
        }
        return corpo;
    }

    private Map<String, Object> montarPagador(Usuario cliente, String documento) {
        Map<String, Object> pagador = new LinkedHashMap<>();
        pagador.put("email", emailPagadorTeste.isBlank() ? cliente.getEmail() : emailPagadorTeste);

        String[] nome = cliente.getNome().trim().split("\\s+", 2);
        pagador.put("first_name", nome[0]);
        if (nome.length > 1) {
            pagador.put("last_name", nome[1]);
        }
        if (documento != null && !documento.isBlank()) {
            pagador.put("identification", Map.of("type", "CPF", "number", documento));
        }
        return pagador;
    }
}
