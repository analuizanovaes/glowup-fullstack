package com.glowup.api.service;

import com.glowup.api.domain.MetodoPagamento;
import com.glowup.api.domain.Pagamento;
import com.glowup.api.domain.Pedido;
import com.glowup.api.domain.StatusPagamento;
import com.glowup.api.domain.StatusPedido;
import com.glowup.api.dto.DadosPagamentoGatewayDTO;
import com.glowup.api.dto.PagamentoRespostaDTO;
import com.glowup.api.exception.ConflitoDeEstadoException;
import com.glowup.api.exception.RecursoNaoEncontradoException;
import com.glowup.api.repository.DevolucaoRepository;
import com.glowup.api.repository.PagamentoRepository;
import com.glowup.api.repository.PedidoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Máquina de estados do pagamento. Todas as transições ocorrem com o pedido bloqueado
 * (SELECT ... FOR UPDATE), o que torna webhooks repetidos ou concorrentes idempotentes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConciliacaoPagamentoService {

    private static final String ORIGEM_RESERVA_EXPIRADA = "SISTEMA:RESERVA_EXPIRADA";

    private final PedidoRepository pedidoRepository;
    private final ParcelaService parcelaService;
    private final PagamentoRepository pagamentoRepository;
    private final PedidoStatusService pedidoStatusService;
    private final EstornoEstoqueService estornoEstoqueService;
    private final CupomService cupomService;
    private final RegistroFinanceiroService registroFinanceiroService;
    private final ApplicationEventPublisher publicadorEventos;
    private final MercadoPagoGateway gateway;
    private final CrmAdminService crmAdminService;
    private final DevolucaoRepository devolucaoRepository;

    @Transactional
    public PagamentoRespostaDTO registrarNovoPagamento(Long pedidoId, MetodoPagamento metodo, int parcelas,
                                                       LocalDateTime pixExpiraEm, DadosPagamentoGatewayDTO dados) {
        Pedido pedido = bloquearPedido(pedidoId);
        if (pagamentoRepository.existsByPedidoId(pedidoId)) {
            throw new ConflitoDeEstadoException("Este pedido já possui um pagamento registrado.");
        }
        if (pedido.getStatus() != StatusPedido.AGUARDANDO_PAGAMENTO) {
            throw new ConflitoDeEstadoException("O pedido não está aguardando pagamento.");
        }

        Pagamento pagamento = Pagamento.builder()
                .pedido(pedido)
                .metodo(metodo)
                .status(StatusPagamento.PENDENTE)
                .valor(pedido.getValorTotal())
                .parcelas(parcelas)
                .idGateway(dados.id())
                .pixExpiraEm(metodo == MetodoPagamento.PIX ? pixExpiraEm : null)
                .build();

        aplicarStatus(pedido, pagamento, dados, "CLIENTE:PAGAMENTO");
        return PagamentoRespostaDTO.de(pagamento, pedido.getId(), pedido.getStatus(), dados.pixQrCodeBase64());
    }

    @Transactional
    public void aplicarResultado(Long pedidoId, DadosPagamentoGatewayDTO dados, String origem) {
        Pedido pedido = bloquearPedido(pedidoId);
        Pagamento pagamento = pagamentoRepository.findByPedidoId(pedidoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Pagamento do pedido " + pedidoId + " ainda não registrado."));
        aplicarStatus(pedido, pagamento, dados, origem);
    }

    @Transactional
    public void cancelarPedidoSemPagamento(Long pedidoId) {
        Pedido pedido = bloquearPedido(pedidoId);
        if (pedido.getStatus() != StatusPedido.AGUARDANDO_PAGAMENTO || pagamentoRepository.existsByPedidoId(pedidoId)) {
            return;
        }
        estornoEstoqueService.devolverItensAoEstoque(pedido);
        cupomService.liberarUso(pedido);
        pedidoStatusService.alterar(pedido, StatusPedido.CANCELADO, ORIGEM_RESERVA_EXPIRADA);
    }

    private Pedido bloquearPedido(Long pedidoId) {
        return pedidoRepository.buscarParaAtualizacao(pedidoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido não encontrado."));
    }

    private void aplicarStatus(Pedido pedido, Pagamento pagamento, DadosPagamentoGatewayDTO dados, String origem) {
        atualizarDadosDoGateway(pagamento, dados);

        switch (traduzirStatus(dados.status())) {
            case APROVADO -> aprovar(pedido, pagamento, dados, origem);
            case REJEITADO -> cancelarPorFalha(pedido, pagamento, StatusPagamento.REJEITADO, origem);
            case CANCELADO -> cancelarPorFalha(pedido, pagamento, StatusPagamento.CANCELADO, origem);
            case ESTORNADO -> registrarEstornoDoGateway(pedido, pagamento);
            default -> log.debug("Pagamento {} segue pendente no gateway.", pagamento.getIdGateway());
        }
        pagamentoRepository.save(pagamento);
        sincronizarParcelas(pagamento);
    }

    // Executado depois do save: as parcelas referenciam o pagamento, que precisa já ter ID.
    private void sincronizarParcelas(Pagamento pagamento) {
        switch (pagamento.getStatus()) {
            case APROVADO -> parcelaService.gerarParcelasDoCartao(pagamento);
            case ESTORNADO -> parcelaService.marcarComoEstornadas(pagamento);
            default -> { }
        }
    }

    private StatusPagamento traduzirStatus(String statusGateway) {
        String status = statusGateway == null ? "" : statusGateway;
        return switch (status) {
            case "approved" -> StatusPagamento.APROVADO;
            case "rejected" -> StatusPagamento.REJEITADO;
            case "cancelled" -> StatusPagamento.CANCELADO;
            case "refunded", "charged_back" -> StatusPagamento.ESTORNADO;
            default -> StatusPagamento.PENDENTE;
        };
    }

    private void atualizarDadosDoGateway(Pagamento pagamento, DadosPagamentoGatewayDTO dados) {
        pagamento.setStatusDetalheGateway(dados.statusDetalhe());
        // Preenche cada campo separadamente: uma resposta anterior pode ter trazido o copia-e-cola sem a imagem.
        if (pagamento.getPixCopiaECola() == null) {
            pagamento.setPixCopiaECola(dados.pixCopiaECola());
        }
        if (pagamento.getPixQrCodeBase64() == null) {
            pagamento.setPixQrCodeBase64(dados.pixQrCodeBase64());
        }
        if (pagamento.getPixLinkTicket() == null) {
            pagamento.setPixLinkTicket(dados.pixLinkTicket());
        }
    }

    private void aprovar(Pedido pedido, Pagamento pagamento, DadosPagamentoGatewayDTO dados, String origem) {
        if (pagamento.getStatus() == StatusPagamento.APROVADO) {
            return;
        }
        if (!aprovacaoConfereComOPedido(pedido, pagamento, dados)) {
            return;
        }
        registrarValoresAprovados(pagamento, dados);
        pagamento.setStatus(StatusPagamento.APROVADO);
        pagamento.setDataPagamento(LocalDateTime.now());

        if (pedido.getStatus() != StatusPedido.AGUARDANDO_PAGAMENTO) {
            String resultado = "Pagamento aprovado após o pedido " + pedido.getStatus()
                    + "; foi solicitado estorno automático.";
            try {
                gateway.estornarPagamento(pagamento.getIdGateway(),
                        "conciliacao-atrasada-" + pagamento.getIdGateway(), null);
                pagamento.setStatus(StatusPagamento.ESTORNADO);
                registroFinanceiroService.marcarComoEstornado(pedido.getId());
                resultado = "Pagamento aprovado após cancelamento e estornado automaticamente.";
            } catch (RuntimeException falhaEstorno) {
                log.error("Pagamento {} aprovado para o pedido {} já {}, mas não foi possível estornar.",
                        pagamento.getIdGateway(), pedido.getId(), pedido.getStatus(), falhaEstorno);
                resultado += " O estorno falhou e requer conciliação manual.";
            }
            crmAdminService.criarNotificacao("CONCILIACAO_PAGAMENTO", "Pagamento fora do estado do pedido",
                    "Pedido #" + pedido.getId() + ": " + resultado, pedido.getId());
            return;
        }

        // O estoque já foi baixado no checkout; a aprovação apenas torna essa baixa definitiva.
        pedidoStatusService.alterar(pedido, StatusPedido.PAGO, origem);
        registroFinanceiroService.registrar(pedido, pagamento);
        publicadorEventos.publishEvent(new PagamentoAprovadoEvento(pedido.getId()));
    }

    /**
     * Defesa extra: só aceita a aprovação se o valor e a referência informados pelo gateway fecham com o pedido.
     * Em caso de divergência o pagamento permanece pendente e a administradora é avisada para conciliar à mão.
     */
    private boolean aprovacaoConfereComOPedido(Pedido pedido, Pagamento pagamento, DadosPagamentoGatewayDTO dados) {
        boolean valorDiverge = dados.valorTransacao() != null
                && dados.valorTransacao().compareTo(pagamento.getValor()) != 0;
        boolean referenciaDiverge = dados.referenciaExterna() != null
                && !dados.referenciaExterna().equals(String.valueOf(pedido.getId()));
        if (!valorDiverge && !referenciaDiverge) {
            return true;
        }
        log.error("Aprovação do pagamento {} NÃO aplicada ao pedido {}: valor do gateway {} (esperado {}), "
                        + "referência do gateway '{}'.", pagamento.getIdGateway(), pedido.getId(),
                dados.valorTransacao(), pagamento.getValor(), dados.referenciaExterna());
        crmAdminService.criarNotificacao("CONCILIACAO_PAGAMENTO", "Pagamento aprovado com dados divergentes",
                "O pagamento " + pagamento.getIdGateway() + " do pedido #" + pedido.getId()
                        + " foi aprovado no Mercado Pago com valor ou referência diferentes do pedido. "
                        + "Confira no painel do Mercado Pago antes de liberar o pedido.", pedido.getId());
        return false;
    }

    private void registrarValoresAprovados(Pagamento pagamento, DadosPagamentoGatewayDTO dados) {
        BigDecimal valorTransacao = dados.valorTransacao() != null ? dados.valorTransacao() : pagamento.getValor();
        BigDecimal valorLiquido = dados.valorLiquido() != null && dados.valorLiquido().signum() > 0
                ? dados.valorLiquido() : null;

        pagamento.setJuros(calcularJuros(valorTransacao, dados.valorTotalPago()));
        pagamento.setValorLiquido(valorLiquido);
        pagamento.setTaxaGateway(valorLiquido != null ? valorTransacao.subtract(valorLiquido).max(BigDecimal.ZERO) : null);

        if (pagamento.getParcelas() != null && pagamento.getParcelas() > 1
                && pagamento.getJuros().signum() == 0) {
            log.warn("Pedido parcelado em {}x sem juros para a compradora. Confirme no painel do Mercado Pago "
                    + "que os juros do parcelamento são pagos pelo comprador.", pagamento.getParcelas());
        }
    }

    private BigDecimal calcularJuros(BigDecimal valorTransacao, BigDecimal valorTotalPago) {
        if (valorTransacao == null || valorTotalPago == null) {
            return BigDecimal.ZERO;
        }
        return valorTotalPago.subtract(valorTransacao).max(BigDecimal.ZERO);
    }

    private void cancelarPorFalha(Pedido pedido, Pagamento pagamento, StatusPagamento novoStatus, String origem) {
        if (pagamento.getStatus() == StatusPagamento.APROVADO) {
            log.warn("Evento {} ignorado: pagamento {} já aprovado.", novoStatus, pagamento.getIdGateway());
            return;
        }
        pagamento.setStatus(novoStatus);

        // Somente o primeiro evento devolve o estoque; repetições encontram o pedido já cancelado.
        if (pedido.getStatus() == StatusPedido.AGUARDANDO_PAGAMENTO) {
            estornoEstoqueService.devolverItensAoEstoque(pedido);
            cupomService.liberarUso(pedido);
            pedidoStatusService.alterar(pedido, StatusPedido.CANCELADO, origem);
        }
    }

    private void registrarEstornoDoGateway(Pedido pedido, Pagamento pagamento) {
        if (pagamento.getStatus() == StatusPagamento.ESTORNADO) {
            return;
        }
        // Estorno gerado pela própria devolução aprovada: o recebimento (DevolucaoService) é quem conclui o pagamento,
        // o estoque e o status DEVOLVIDO. Tratar aqui cancelaria o pedido e faria o recebimento falhar.
        if (devolucaoRepository.existsByPedidoIdAndStatusIn(pedido.getId(),
                java.util.Set.of(com.glowup.api.domain.StatusDevolucao.APROVADA))) {
            log.info("Estorno do pagamento {} pertence à devolução aprovada do pedido {}; conclusão no recebimento.",
                    pagamento.getIdGateway(), pedido.getId());
            return;
        }
        boolean haviaPagamentoAprovado = pagamento.getStatus() == StatusPagamento.APROVADO;
        pagamento.setStatus(StatusPagamento.ESTORNADO);
        if (haviaPagamentoAprovado) {
            registroFinanceiroService.marcarComoEstornado(pedido.getId());
        }
        if (pedido.getStatus() == StatusPedido.AGUARDANDO_PAGAMENTO) {
            // O gateway pode informar estorno/chargeback antes de a confirmação de aprovação
            // ser conciliada localmente. Nesse caso o pedido ainda reservou estoque e cupom.
            estornoEstoqueService.devolverItensAoEstoque(pedido);
            cupomService.liberarUso(pedido);
        }
        if (pedido.getStatus() != StatusPedido.CANCELADO && pedido.getStatus() != StatusPedido.DEVOLVIDO) {
            pedidoStatusService.alterar(pedido, StatusPedido.CANCELADO, "GATEWAY:ESTORNO");
        }
        crmAdminService.criarNotificacao("CONCILIACAO_PAGAMENTO", "Pagamento estornado externamente",
                "O pedido #" + pedido.getId() + " foi estornado/contestado no Mercado Pago. Confirme o retorno dos itens "
                        + "antes de ajustar o estoque.", pedido.getId());
    }
}
