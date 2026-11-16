package com.glowup.api.service;

import com.glowup.api.domain.Devolucao;
import com.glowup.api.domain.HistoricoStatusPedido;
import com.glowup.api.domain.ItemDevolucao;
import com.glowup.api.domain.ItemPedido;
import com.glowup.api.domain.Pagamento;
import com.glowup.api.domain.Pedido;
import com.glowup.api.domain.StatusDevolucao;
import com.glowup.api.domain.StatusPagamento;
import com.glowup.api.domain.StatusPedido;
import com.glowup.api.dto.AnaliseDevolucaoDTO;
import com.glowup.api.dto.DevolucaoRespostaDTO;
import com.glowup.api.dto.ItemDevolucaoRequisicaoDTO;
import com.glowup.api.dto.ItemLacreDTO;
import com.glowup.api.dto.SolicitacaoDevolucaoDTO;
import com.glowup.api.exception.ConflitoDeEstadoException;
import com.glowup.api.exception.RecursoNaoEncontradoException;
import com.glowup.api.model.Usuario;
import com.glowup.api.repository.DevolucaoRepository;
import com.glowup.api.repository.HistoricoStatusPedidoRepository;
import com.glowup.api.repository.PagamentoRepository;
import com.glowup.api.repository.PedidoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Máquina de estados da devolução: SOLICITADA -> APROVADA -> RECEBIDA, ou SOLICITADA -> REJEITADA.
 * A reintegração ao estoque e o estorno financeiro só ocorrem na transição APROVADA -> RECEBIDA (RD03).
 */
@Service
@RequiredArgsConstructor
public class DevolucaoService {

    private static final Set<StatusDevolucao> STATUS_EM_ANDAMENTO =
            Set.of(StatusDevolucao.SOLICITADA, StatusDevolucao.APROVADA);

    private final DevolucaoRepository devolucaoRepository;
    private final PedidoRepository pedidoRepository;
    private final PedidoStatusService pedidoStatusService;
    private final PagamentoRepository pagamentoRepository;
    private final HistoricoStatusPedidoRepository historicoRepository;
    private final EstornoEstoqueService estornoEstoqueService;
    private final RegistroFinanceiroService registroFinanceiroService;
    private final MercadoPagoGateway gateway;
    private final EstornoService estornoService;
    private final ParcelaService parcelaService;
    private final PlatformTransactionManager transactionManager;

    @Value("${glowup.devolucao.prazo-dias:7}")
    private long prazoDias;

    @Transactional
    public DevolucaoRespostaDTO solicitar(Usuario cliente, Long pedidoId, SolicitacaoDevolucaoDTO dto) {
        Pedido pedido = pedidoRepository.buscarParaAtualizacao(pedidoId)
                .filter(encontrado -> encontrado.getCliente() != null
                        && encontrado.getCliente().getId().equals(cliente.getId()))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido não encontrado."));

        validarPedidoEntregue(pedido);
        validarPrazoLegal(pedido);

        if (devolucaoRepository.existsByPedidoIdAndStatusIn(pedidoId, STATUS_EM_ANDAMENTO)) {
            throw new ConflitoDeEstadoException("Já existe uma devolução em andamento para este pedido.");
        }

        List<Devolucao> recebidas = devolucaoRepository.findByPedidoIdAndStatus(pedidoId, StatusDevolucao.RECEBIDA);
        Map<Long, Integer> jaDevolvidas = quantidadesJaDevolvidas(pedido, recebidas);
        if (!recebidas.isEmpty() && quitaOPedido(pedido, Map.of(), jaDevolvidas)) {
            throw new ConflitoDeEstadoException("Todos os itens deste pedido já foram devolvidos.");
        }

        List<ItemSolicitado> itensSolicitados = consolidarItens(pedido, dto,
                quantidadesJaProcessadas(pedido, recebidas));
        BigDecimal valorEstornoCalculado = calcularValorEstorno(pedido, itensSolicitados, jaDevolvidas,
                somarValoresEstornados(recebidas));

        Devolucao devolucao = Devolucao.builder()
                .pedido(pedido)
                .usuario(pedido.getCliente())
                .status(StatusDevolucao.SOLICITADA)
                .motivo(dto.motivo())
                .arrependimento(Boolean.TRUE.equals(dto.arrependimento()))
                .dataSolicitacao(LocalDateTime.now())
                .build();

        devolucao.setValorEstornado(valorEstornoCalculado);
        for (ItemSolicitado solicitado : itensSolicitados) {
            devolucao.getItens().add(ItemDevolucao.builder()
                    .devolucao(devolucao)
                    .itemPedido(solicitado.item())
                    .produto(solicitado.item().getProduto())
                    .produtoId(solicitado.item().getProdutoIdPersistido())
                    .quantidade(solicitado.quantidade())
                    .build());
        }

        Devolucao salvo = devolucaoRepository.save(devolucao);
        return DevolucaoRespostaDTO.de(salvo, pedidoId);
    }

    @Transactional
    public DevolucaoRespostaDTO analisar(Long devolucaoId, AnaliseDevolucaoDTO dto) {
        Devolucao devolucao = buscarComBloqueio(devolucaoId);
        exigirStatus(devolucao, StatusDevolucao.SOLICITADA, "analisada");

        if (ehArrependimentoArt49(devolucao)) {
            analisarArrependimentoArt49(devolucao, dto);
            devolucaoRepository.save(devolucao);
            return DevolucaoRespostaDTO.de(devolucao, devolucao.getPedido().getId());
        }

        boolean lacrado = Boolean.TRUE.equals(dto.produtoLacrado());
        if ((dto.itens() == null || dto.itens().isEmpty()) && dto.produtoLacrado() == null) {
            throw new IllegalArgumentException("Informe o estado do lacre para o produto ou para cada item.");
        }
        if (dto.itens() != null && !dto.itens().isEmpty()) {
            Map<Long, Boolean> lacres = new LinkedHashMap<>();
            for (ItemLacreDTO item : dto.itens()) {
                if (item == null || item.idItemDevolucao() == null || item.produtoLacrado() == null
                        || lacres.putIfAbsent(item.idItemDevolucao(), item.produtoLacrado()) != null) {
                    throw new IllegalArgumentException("Informe uma avaliação válida e única para cada item devolvido.");
                }
            }
            if (lacres.size() != devolucao.getItens().size()) {
                throw new IllegalArgumentException("Avalie o lacre de todos os itens devolvidos.");
            }
            for (ItemDevolucao item : devolucao.getItens()) {
                Boolean itemLacrado = lacres.get(item.getId());
                if (itemLacrado == null) {
                    throw new IllegalArgumentException("A avaliação contém item que não pertence a esta devolução.");
                }
                item.setProdutoLacrado(itemLacrado);
            }
            boolean todosLacrados = devolucao.getItens().stream().allMatch(i -> Boolean.TRUE.equals(i.getProdutoLacrado()));
            boolean algumLacrado = devolucao.getItens().stream().anyMatch(i -> Boolean.TRUE.equals(i.getProdutoLacrado()));
            lacrado = todosLacrados;
            if (!todosLacrados) {
                devolucao.setValorEstornado(calcularEstornoSomenteLacrados(devolucao));
            }
            devolucao.setProdutoLacrado(algumLacrado);
            devolucao.setObservacaoAnalise(dto.observacao());
            devolucao.setDataAnalise(LocalDateTime.now());
            devolucao.setStatus(algumLacrado ? StatusDevolucao.APROVADA : StatusDevolucao.REJEITADA);
            devolucaoRepository.save(devolucao);
            return DevolucaoRespostaDTO.de(devolucao, devolucao.getPedido().getId());
        }
        devolucao.setProdutoLacrado(lacrado);
        devolucao.setObservacaoAnalise(dto.observacao());
        devolucao.setDataAnalise(LocalDateTime.now());
        devolucao.setStatus(lacrado ? StatusDevolucao.APROVADA : StatusDevolucao.REJEITADA);
        for (ItemDevolucao item : devolucao.getItens()) {
            item.setProdutoLacrado(lacrado);
        }

        devolucaoRepository.save(devolucao);
        return DevolucaoRespostaDTO.de(devolucao, devolucao.getPedido().getId());
    }

    /** No arrependimento do CDC, o lacre não condiciona a aprovação financeira; só controla a volta ao estoque. */
    private void analisarArrependimentoArt49(Devolucao devolucao, AnaliseDevolucaoDTO dto) {
        Map<Long, Boolean> lacres = new LinkedHashMap<>();
        if (dto.itens() != null && !dto.itens().isEmpty()) {
            for (ItemLacreDTO item : dto.itens()) {
                if (item == null || item.idItemDevolucao() == null || item.produtoLacrado() == null
                        || lacres.putIfAbsent(item.idItemDevolucao(), item.produtoLacrado()) != null) {
                    throw new IllegalArgumentException("Informe uma avaliação válida e única para cada item devolvido.");
                }
            }
            if (lacres.size() != devolucao.getItens().size()) {
                throw new IllegalArgumentException("Avalie o lacre de todos os itens devolvidos.");
            }
        } else if (dto.produtoLacrado() != null) {
            devolucao.getItens().forEach(item -> lacres.put(item.getId(), dto.produtoLacrado()));
        }

        // Ausência de avaliação é tratada como não lacrado para evitar reintegrar mercadoria ao estoque.
        for (ItemDevolucao item : devolucao.getItens()) {
            item.setProdutoLacrado(lacres.containsKey(item.getId()) ? lacres.get(item.getId()) : null);
        }
        boolean todosLacrados = devolucao.getItens().isEmpty()
                ? Boolean.TRUE.equals(dto.produtoLacrado())
                : devolucao.getItens().stream().allMatch(i -> Boolean.TRUE.equals(i.getProdutoLacrado()));
        devolucao.setProdutoLacrado(todosLacrados);
        devolucao.setObservacaoAnalise(dto.observacao());
        devolucao.setDataAnalise(LocalDateTime.now());
        // Mantém o valor de estorno calculado no pedido; lacre violado não pode reduzir o direito de arrependimento.
        devolucao.setStatus(StatusDevolucao.APROVADA);
    }

    private boolean ehArrependimentoArt49(Devolucao devolucao) {
        return devolucao.isArrependimento();
    }

    public DevolucaoRespostaDTO confirmarRecebimento(Long devolucaoId) {
        TransactionTemplate transacao = new TransactionTemplate(transactionManager);
        RecebimentoPreparado preparado = transacao.execute(status -> prepararRecebimento(devolucaoId));
        if (preparado == null) {
            throw new IllegalStateException("Não foi possível preparar o recebimento da devolução.");
        }
        if (preparado.jaRecebida() != null) {
            return preparado.jaRecebida();
        }

        // Chamada remota fora da transação e do lock do pedido. A chave permite retomar o registro
        // local com segurança caso o banco falhe depois de o Mercado Pago concluir o estorno.
        String idEstorno = gateway.estornarPagamento(preparado.idGateway(), preparado.chaveIdempotencia(),
                preparado.valorParcial());
        return transacao.execute(status -> concluirRecebimento(preparado, idEstorno));
    }

    private record RecebimentoPreparado(Long devolucaoId, String idGateway, String chaveIdempotencia,
                                       BigDecimal valorParcial, boolean devolucaoTotal,
                                       boolean pedidoQuitado, DevolucaoRespostaDTO jaRecebida) {}

    private RecebimentoPreparado prepararRecebimento(Long devolucaoId) {
        Devolucao devolucao = buscarComBloqueio(devolucaoId);
        if (devolucao.getStatus() == StatusDevolucao.RECEBIDA) {
            return new RecebimentoPreparado(devolucaoId, null, null, null, false, false,
                    DevolucaoRespostaDTO.de(devolucao, devolucao.getPedido().getId()));
        }
        exigirStatus(devolucao, StatusDevolucao.APROVADA, "recebida");
        Long pedidoId = devolucao.getPedido().getId();
        Pedido pedido = pedidoRepository.buscarParaAtualizacao(pedidoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido não encontrado."));
        Pagamento pagamento = pagamentoRepository.findByPedidoId(pedidoId)
                .filter(encontrado -> encontrado.getStatus() == StatusPagamento.APROVADO)
                .orElseThrow(() -> new ConflitoDeEstadoException("O pedido não possui pagamento aprovado para estornar."));
        List<Devolucao> anteriores = devolucaoRepository.findByPedidoIdAndStatus(pedidoId, StatusDevolucao.RECEBIDA);
        boolean devolucaoTotal = anteriores.isEmpty() && devolucaoEhTotal(devolucao, pedido);
        boolean pedidoQuitado = devolucaoTotal || quitaOPedido(pedido, quantidadesDaDevolucao(devolucao),
                quantidadesJaDevolvidas(pedido, anteriores));
        BigDecimal valorParcial = devolucao.getValorEstornado();
        if (!devolucaoTotal && (valorParcial == null || valorParcial.signum() <= 0)) {
            throw new ConflitoDeEstadoException(
                    "O valor do estorno parcial deve ser maior que zero; nenhum estorno foi enviado ao gateway.");
        }
        return new RecebimentoPreparado(devolucaoId, pagamento.getIdGateway(), "devolucao-" + devolucao.getId(),
                devolucaoTotal ? null : valorParcial, devolucaoTotal, pedidoQuitado, null);
    }

    private DevolucaoRespostaDTO concluirRecebimento(RecebimentoPreparado preparado, String idEstorno) {
        Devolucao devolucao = buscarComBloqueio(preparado.devolucaoId());
        if (devolucao.getStatus() == StatusDevolucao.RECEBIDA) {
            return DevolucaoRespostaDTO.de(devolucao, devolucao.getPedido().getId());
        }
        exigirStatus(devolucao, StatusDevolucao.APROVADA, "recebida");
        Long pedidoId = devolucao.getPedido().getId();
        Pedido pedido = pedidoRepository.buscarParaAtualizacao(pedidoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido não encontrado."));
        Pagamento pagamento = pagamentoRepository.findByPedidoId(pedidoId)
                .filter(encontrado -> encontrado.getStatus() == StatusPagamento.APROVADO)
                .orElseThrow(() -> new ConflitoDeEstadoException("O pedido não possui pagamento aprovado para estornar."));
        estornoEstoqueService.devolverItensDaDevolucao(devolucao);
        registrarEstornoFinanceiro(devolucao, pagamento, preparado.pedidoQuitado(), idEstorno);
        if (preparado.pedidoQuitado()) {
            registroFinanceiroService.marcarComoEstornado(pedidoId);
            pedidoStatusService.alterar(pedido, StatusPedido.DEVOLVIDO, "DEVOLUCAO:RECEBIDA");
        } else {
            registroFinanceiroService.ajustarParaDevolucaoParcial(pedidoId, devolucao.getValorEstornado(),
                    devolucao.getItens());
        }
        devolucao.setStatus(StatusDevolucao.RECEBIDA);
        devolucao.setDataRecebimento(LocalDateTime.now());
        devolucaoRepository.save(devolucao);
        return DevolucaoRespostaDTO.de(devolucao, pedidoId);
    }

    @Transactional(readOnly = true)
    public List<DevolucaoRespostaDTO> listarDaCliente(Usuario cliente) {
        return devolucaoRepository.findByUsuarioIdOrderByDataSolicitacaoDesc(cliente.getId()).stream()
                .map(devolucao -> DevolucaoRespostaDTO.de(devolucao, devolucao.getPedido().getId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DevolucaoRespostaDTO> listarParaAdmin(StatusDevolucao filtroStatus) {
        List<Devolucao> devolucoes = filtroStatus == null
                ? devolucaoRepository.findAllByOrderByDataSolicitacaoDesc()
                : devolucaoRepository.findByStatusOrderByDataSolicitacaoDesc(filtroStatus);
        return devolucoes.stream()
                .map(devolucao -> DevolucaoRespostaDTO.de(devolucao, devolucao.getPedido().getId()))
                .toList();
    }

    private Devolucao buscarComBloqueio(Long devolucaoId) {
        return devolucaoRepository.buscarParaAtualizacao(devolucaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Devolução não encontrada."));
    }

    private void exigirStatus(Devolucao devolucao, StatusDevolucao esperado, String acao) {
        if (devolucao.getStatus() != esperado) {
            throw new ConflitoDeEstadoException(
                    "A devolução está " + devolucao.getStatus() + " e não pode ser " + acao + ".");
        }
    }

    private void validarPedidoEntregue(Pedido pedido) {
        if (pedido.getStatus() != StatusPedido.ENTREGUE) {
            throw new IllegalArgumentException("A devolução só pode ser solicitada para pedidos entregues.");
        }
    }

    private void validarPrazoLegal(Pedido pedido) {
        LocalDateTime dataEntrega = historicoRepository
                .findFirstByPedidoIdAndStatusNovoOrderByOcorridoEmDesc(pedido.getId(), StatusPedido.ENTREGUE)
                .map(HistoricoStatusPedido::getOcorridoEm)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Data de entrega não registrada para este pedido. Contate a administradora."));

        if (LocalDateTime.now().isAfter(dataEntrega.plusDays(prazoDias))) {
            throw new IllegalArgumentException("O prazo de " + prazoDias + " dias para devolução expirou.");
        }
    }

    /**
     * Total: estorno sem valor (o Mercado Pago devolve tudo, inclusive juros) e o pagamento vira ESTORNADO.
     * Parcial: envia o valor calculado ao gateway; o pagamento só vira ESTORNADO quando a devolução
     * (somada às anteriores) quita todos os itens do pedido, senão continua APROVADO.
     */
    private void registrarEstornoFinanceiro(Devolucao devolucao, Pagamento pagamento, boolean pedidoQuitado,
                                            String idEstorno) {
        if (pedidoQuitado) {
            pagamento.setStatus(StatusPagamento.ESTORNADO);
            pagamentoRepository.save(pagamento);
            parcelaService.marcarComoEstornadas(pagamento);
        }

        devolucao.setIdEstornoGateway(idEstorno);
        estornoService.registrarConcluido(devolucao, pagamento, devolucao.getValorEstornado());
    }

    private record ItemSolicitado(ItemPedido item, int quantidade) {
    }

    /**
     * Soma itens repetidos, confere que pertencem ao pedido e que não excedem o comprado
     * (descontadas as unidades de devoluções anteriores já recebidas).
     */
    private List<ItemSolicitado> consolidarItens(Pedido pedido, SolicitacaoDevolucaoDTO dto,
                                                 Map<Long, Integer> jaDevolvidas) {
        if (dto.itensDevolvidos() == null || dto.itensDevolvidos().isEmpty()) {
            throw new IllegalArgumentException("Informe ao menos um item para devolução.");
        }
        Map<Long, Integer> quantidadePorItem = new LinkedHashMap<>();
        for (ItemDevolucaoRequisicaoDTO itemDto : dto.itensDevolvidos()) {
            if (itemDto == null || itemDto.idItemPedido() == null
                    || itemDto.quantidade() == null || itemDto.quantidade() <= 0) {
                throw new IllegalArgumentException("Informe o item e uma quantidade maior que zero.");
            }
            quantidadePorItem.merge(itemDto.idItemPedido(), itemDto.quantidade(), Integer::sum);
        }

        List<ItemSolicitado> consolidados = new java.util.ArrayList<>();
        for (Map.Entry<Long, Integer> entrada : quantidadePorItem.entrySet()) {
            ItemPedido item = pedido.getItens().stream()
                    .filter(i -> i.getId().equals(entrada.getKey()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("O item informado não pertence a este pedido."));
            int jaDevolvida = jaDevolvidas.getOrDefault(item.getId(), 0);
            if (entrada.getValue() > item.getQuantidade() - jaDevolvida) {
                throw new IllegalArgumentException(jaDevolvida == 0
                        ? "A quantidade informada é maior que a comprada no pedido."
                        : "A quantidade informada é maior que a restante para devolução neste pedido.");
            }
            consolidados.add(new ItemSolicitado(item, entrada.getValue()));
        }
        return consolidados;
    }

    /**
     * Devolução que zera o pedido: devolve o que ainda não foi estornado (inclui o frete). Parcial: soma
     * preço x quantidade dos itens devolvidos, já abatendo a fração proporcional do cupom. Nunca passa
     * do que resta a estornar do total pago.
     */
    private BigDecimal calcularValorEstorno(Pedido pedido, List<ItemSolicitado> solicitados,
                                            Map<Long, Integer> jaDevolvidas, BigDecimal jaEstornado) {
        BigDecimal totalPedido = pedido.getValorTotal();
        BigDecimal restante = totalPedido == null ? null : totalPedido.subtract(jaEstornado).max(BigDecimal.ZERO);

        Map<Long, Integer> quantidadePorItem = new LinkedHashMap<>();
        solicitados.forEach(s -> quantidadePorItem.put(s.item().getId(), s.quantidade()));
        if (restante != null && quitaOPedido(pedido, quantidadePorItem, jaDevolvidas)) {
            return restante;
        }

        BigDecimal valor = BigDecimal.ZERO;
        for (ItemSolicitado solicitado : solicitados) {
            valor = valor.add(solicitado.item().getPrecoUnitario()
                    .multiply(BigDecimal.valueOf(solicitado.quantidade())));
        }

        BigDecimal subtotal = pedido.getSubtotal();
        BigDecimal desconto = pedido.getDescontoCupom();
        if (subtotal != null && subtotal.signum() > 0 && desconto != null && desconto.signum() > 0) {
            BigDecimal fracaoDoCupom = desconto.divide(subtotal, 10, RoundingMode.HALF_UP);
            valor = valor.subtract(valor.multiply(fracaoDoCupom));
        }

        valor = valor.setScale(2, RoundingMode.HALF_UP);
        if (restante != null && valor.compareTo(restante) > 0) {
            return restante;
        }
        return valor;
    }

    /** Verdadeiro quando as unidades já devolvidas somadas às desta devolução cobrem todos os itens do pedido. */
    private boolean quitaOPedido(Pedido pedido, Map<Long, Integer> quantidadePorItem, Map<Long, Integer> jaDevolvidas) {
        return pedido.getItens().stream().allMatch(item ->
                jaDevolvidas.getOrDefault(item.getId(), 0) + quantidadePorItem.getOrDefault(item.getId(), 0)
                        >= item.getQuantidade());
    }

    /** Unidades por item já recebidas em devoluções anteriores. Devolução antiga, sem itens, valia o pedido todo. */
    private Map<Long, Integer> quantidadesJaDevolvidas(Pedido pedido, List<Devolucao> recebidas) {
        Map<Long, Integer> quantidades = new LinkedHashMap<>();
        for (Devolucao anterior : recebidas) {
            if (anterior.getItens() == null || anterior.getItens().isEmpty()) {
                pedido.getItens().forEach(item -> quantidades.merge(item.getId(), item.getQuantidade(), Integer::sum));
            } else {
                anterior.getItens().stream().filter(item -> ehArrependimentoArt49(anterior)
                || Boolean.TRUE.equals(item.getProdutoLacrado())).forEach(item ->
                        quantidades.merge(item.getItemPedido().getId(), item.getQuantidade(), Integer::sum));
            }
        }
        return quantidades;
    }

    /**
     * Unidades que já voltaram à loja em devoluções recebidas, com ou sem lacre. Impede pedir de novo a devolução
     * de itens já recebidos e recusados por lacre violado (sem reembolso, portanto fora de quantidadesJaDevolvidas).
     */
    private Map<Long, Integer> quantidadesJaProcessadas(Pedido pedido, List<Devolucao> recebidas) {
        Map<Long, Integer> quantidades = new LinkedHashMap<>();
        for (Devolucao anterior : recebidas) {
            if (anterior.getItens() == null || anterior.getItens().isEmpty()) {
                pedido.getItens().forEach(item -> quantidades.merge(item.getId(), item.getQuantidade(), Integer::sum));
            } else {
                anterior.getItens().forEach(item ->
                        quantidades.merge(item.getItemPedido().getId(), item.getQuantidade(), Integer::sum));
            }
        }
        return quantidades;
    }

    private Map<Long, Integer> quantidadesDaDevolucao(Devolucao devolucao) {
        Map<Long, Integer> quantidades = new LinkedHashMap<>();
        if (devolucao.getItens() != null) {
            devolucao.getItens().stream().filter(item -> ehArrependimentoArt49(devolucao)
                || Boolean.TRUE.equals(item.getProdutoLacrado())).forEach(item ->
                    quantidades.merge(item.getItemPedido().getId(), item.getQuantidade(), Integer::sum));
        }
        return quantidades;
    }

    private BigDecimal somarValoresEstornados(List<Devolucao> recebidas) {
        BigDecimal total = BigDecimal.ZERO;
        for (Devolucao recebida : recebidas) {
            if (recebida.getValorEstornado() != null) {
                total = total.add(recebida.getValorEstornado());
            }
        }
        return total;
    }

    private boolean devolucaoEhTotal(Devolucao devolucao, Pedido pedido) {
        if (devolucao.getItens() == null || devolucao.getItens().isEmpty()) {
            return true;
        }
        return pedido.getItens().stream().allMatch(itemPedido -> devolucao.getItens().stream()
                .filter(item -> ehArrependimentoArt49(devolucao) || Boolean.TRUE.equals(item.getProdutoLacrado()))
                .filter(d -> d.getItemPedido().getId().equals(itemPedido.getId()))
                .mapToInt(ItemDevolucao::getQuantidade).sum() >= itemPedido.getQuantidade());
    }

    private BigDecimal calcularEstornoSomenteLacrados(Devolucao devolucao) {
        BigDecimal valor = BigDecimal.ZERO;
        for (ItemDevolucao item : devolucao.getItens()) {
            if (Boolean.TRUE.equals(item.getProdutoLacrado())) {
                valor = valor.add(item.getItemPedido().getPrecoUnitario()
                        .multiply(BigDecimal.valueOf(item.getQuantidade())));
            }
        }
        Pedido pedido = devolucao.getPedido();
        if (pedido.getSubtotal() != null && pedido.getSubtotal().signum() > 0
                && pedido.getDescontoCupom() != null && pedido.getDescontoCupom().signum() > 0) {
            BigDecimal fracao = pedido.getDescontoCupom().divide(pedido.getSubtotal(), 10, RoundingMode.HALF_UP);
            valor = valor.subtract(valor.multiply(fracao));
        }
        BigDecimal limite = devolucao.getValorEstornado() == null ? valor : devolucao.getValorEstornado();
        BigDecimal calculado = valor.setScale(2, RoundingMode.HALF_UP).min(limite).max(BigDecimal.ZERO);
        return calculado.signum() == 0 ? null : calculado;
    }
}
