package com.glowup.api.service;

import com.glowup.api.domain.Pagamento;
import com.glowup.api.domain.Pedido;
import com.glowup.api.domain.StatusPagamento;
import com.glowup.api.domain.StatusPedido;
import com.glowup.api.dto.LucroRealDTO;
import com.glowup.api.dto.PedidoAdminRespostaDTO;
import com.glowup.api.exception.ConflitoDeEstadoException;
import com.glowup.api.exception.RecursoNaoEncontradoException;
import com.glowup.api.repository.PagamentoRepository;
import com.glowup.api.repository.PedidoRepository;
import com.glowup.api.repository.PedidoEnderecoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class PedidoGestaoService {

    // Pago > Em Separação > Enviado > Entregue. Cancelamento manual só antes do pagamento;
    // pedidos pagos são revertidos pelo fluxo de devolução, que também faz o estorno financeiro.
    private static final Map<StatusPedido, Set<StatusPedido>> TRANSICOES_PERMITIDAS = Map.of(
            StatusPedido.AGUARDANDO_PAGAMENTO, Set.of(StatusPedido.CANCELADO),
            StatusPedido.PAGO, Set.of(StatusPedido.EM_SEPARACAO),
            StatusPedido.EM_SEPARACAO, Set.of(StatusPedido.ENVIADO),
            StatusPedido.ENVIADO, Set.of(StatusPedido.ENTREGUE),
            StatusPedido.ENTREGUE, Set.of(),
            StatusPedido.DEVOLVIDO, Set.of(),
            StatusPedido.CANCELADO, Set.of());

    private final PedidoRepository pedidoRepository;
    private final PedidoEnderecoRepository pedidoEnderecoRepository;
    private final PagamentoRepository pagamentoRepository;
    private final PedidoStatusService pedidoStatusService;
    private final EstornoEstoqueService estornoEstoqueService;
    private final CupomService cupomService;
    private final RegistroFinanceiroService registroFinanceiroService;
    private final MercadoPagoGateway gateway;

    @Transactional(readOnly = true)
    public List<PedidoAdminRespostaDTO> listar(StatusPedido filtroStatus) {
        List<Pedido> pedidos = filtroStatus == null
                ? pedidoRepository.findAllByOrderByDataCriacaoDesc()
                : pedidoRepository.findByStatusOrderByDataCriacaoDesc(filtroStatus);
        Map<Long, LucroRealDTO> lucros = registroFinanceiroService.consultarLucros(
                pedidos.stream().map(Pedido::getId).toList());
        return pedidos.stream().map(p -> paraRespostaAdmin(p, lucros)).toList();
    }

    @Transactional(readOnly = true)
    public Page<PedidoAdminRespostaDTO> listar(StatusPedido filtroStatus, Pageable pageable) {
        Page<Pedido> pedidos = filtroStatus == null
                ? pedidoRepository.findAll(pageable)
                : pedidoRepository.findByStatus(filtroStatus, pageable);
        Map<Long, LucroRealDTO> lucros = registroFinanceiroService.consultarLucros(
                pedidos.getContent().stream().map(Pedido::getId).toList());
        return pedidos.map(p -> paraRespostaAdmin(p, lucros));
    }

    @Transactional(readOnly = true)
    public PedidoAdminRespostaDTO buscar(Long pedidoId) {
        return pedidoRepository.findById(pedidoId)
                .map(this::paraRespostaAdmin)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido não encontrado."));
    }

    @Transactional
    public PedidoAdminRespostaDTO atualizarStatus(Long pedidoId, StatusPedido novoStatus, String emailAdministradora) {
        Pedido pedido = pedidoRepository.buscarParaAtualizacao(pedidoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido não encontrado."));

        validarTransicao(pedido.getStatus(), novoStatus);
        if (pedido.isConferenciaEnderecoPendente()
                && (novoStatus == StatusPedido.EM_SEPARACAO || novoStatus == StatusPedido.ENVIADO)) {
            throw new ConflitoDeEstadoException("Confira o endereço de entrega antes de separar ou enviar "
                    + "(o ViaCEP estava fora do ar no checkout). Use PATCH /api/admin/pedidos/{id}/endereco-conferido.");
        }
        if (novoStatus == StatusPedido.CANCELADO) {
            cancelarPedidoNaoPago(pedido);
        }
        pedidoStatusService.alterar(pedido, novoStatus, "ADMIN:" + emailAdministradora);
        return paraRespostaAdmin(pedido);
    }

    @Transactional
    public PedidoAdminRespostaDTO marcarEnderecoConferido(Long pedidoId, String emailAdministradora) {
        Pedido pedido = pedidoRepository.buscarParaAtualizacao(pedidoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido não encontrado."));
        pedido.setConferenciaEnderecoPendente(false);
        pedidoRepository.save(pedido);
        log.info("Endereço do pedido {} conferido por {}.", pedidoId, emailAdministradora);
        return paraRespostaAdmin(pedido);
    }

    @Transactional
    public void cancelarPedidoCliente(Long pedidoId, Long clienteId) {
        Pedido pedido = pedidoRepository.buscarParaAtualizacao(pedidoId)
                .filter(encontrado -> encontrado.getCliente() != null
                        && encontrado.getCliente().getId().equals(clienteId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido não encontrado."));
        if (pedido.getStatus() == StatusPedido.CANCELADO) {
            return;
        }
        if (pedido.getStatus() != StatusPedido.AGUARDANDO_PAGAMENTO) {
            throw new ConflitoDeEstadoException("Só é possível cancelar um pedido aguardando pagamento.");
        }
        cancelarPedidoNaoPago(pedido);
        pedidoStatusService.alterar(pedido, StatusPedido.CANCELADO, "CLIENTE:" + clienteId);
    }

    private PedidoAdminRespostaDTO paraRespostaAdmin(Pedido pedido) {
        LucroRealDTO lucro = pedido.getStatus() == StatusPedido.AGUARDANDO_PAGAMENTO
                || pedido.getStatus() == StatusPedido.CANCELADO || pedido.getStatus() == StatusPedido.DEVOLVIDO
                ? LucroRealDTO.indisponivel("Pedido sem pagamento aprovado")
                : registroFinanceiroService.consultarLucro(pedido.getId());
        return respostaAdmin(pedido, lucro);
    }

    private PedidoAdminRespostaDTO paraRespostaAdmin(Pedido pedido, Map<Long, LucroRealDTO> lucros) {
        LucroRealDTO lucro = pedido.getStatus() == StatusPedido.AGUARDANDO_PAGAMENTO
                || pedido.getStatus() == StatusPedido.CANCELADO || pedido.getStatus() == StatusPedido.DEVOLVIDO
                ? LucroRealDTO.indisponivel("Pedido sem pagamento aprovado")
                : lucros.getOrDefault(pedido.getId(), LucroRealDTO.indisponivel(LucroRealDTO.DADOS_INSUFICIENTES));
        return respostaAdmin(pedido, lucro);
    }

    private PedidoAdminRespostaDTO respostaAdmin(Pedido pedido, LucroRealDTO lucro) {
        return PedidoAdminRespostaDTO.de(pedido, lucro,
                pedidoEnderecoRepository.findByPedidoId(pedido.getId()).orElse(null),
                pagamentoRepository.findByPedidoId(pedido.getId()).orElse(null));
    }

    private void validarTransicao(StatusPedido atual, StatusPedido novo) {
        if (!TRANSICOES_PERMITIDAS.getOrDefault(atual, Set.of()).contains(novo)) {
            throw new ConflitoDeEstadoException("Transição de status inválida: " + atual + " -> " + novo + ".");
        }
    }

    private void cancelarPedidoNaoPago(Pedido pedido) {
        pagamentoRepository.findByPedidoId(pedido.getId()).ifPresent(this::cancelarPagamentoPendente);
        estornoEstoqueService.devolverItensAoEstoque(pedido);
        cupomService.liberarUso(pedido);
    }

    private boolean gatewayJaEncerrouPagamento(String idGateway) {
        try {
            com.glowup.api.dto.DadosPagamentoGatewayDTO dados = gateway.consultarPagamento(idGateway);
            String status = dados == null ? null : dados.status();
            return "cancelled".equals(status) || "rejected".equals(status) || "expired".equals(status);
        } catch (RuntimeException falhaAoConsultar) {
            return false;
        }
    }

    private void cancelarPagamentoPendente(Pagamento pagamento) {
        if (pagamento.getStatus() != StatusPagamento.PENDENTE) {
            return;
        }
        if (pagamento.getIdGateway() != null) {
            try {
                gateway.cancelarPagamento(pagamento.getIdGateway());
            } catch (RuntimeException falhaAoCancelar) {
                // Pix expirado/cancelado no gateway antes da sincronização local: o cancelamento do pedido segue.
                // Qualquer outro estado (aprovado, em análise, gateway fora do ar) mantém o erro e bloqueia o cancelamento.
                if (!gatewayJaEncerrouPagamento(pagamento.getIdGateway())) {
                    throw falhaAoCancelar;
                }
            }
        }
        pagamento.setStatus(StatusPagamento.CANCELADO);
        pagamentoRepository.save(pagamento);
    }

}
