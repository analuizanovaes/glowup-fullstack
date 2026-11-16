package com.glowup.api.service;

import com.glowup.api.dto.PedidoClienteRespostaDTO;
import com.glowup.api.exception.RecursoNaoEncontradoException;
import com.glowup.api.repository.PedidoRepository;
import com.glowup.api.repository.HistoricoStatusPedidoRepository;
import com.glowup.api.domain.StatusPedido;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Consultas de pós-venda da cliente. Toda busca é filtrada pelo ID do usuário autenticado (RF05).
 */
@Service
@RequiredArgsConstructor
public class PedidoClienteService {

    private final PedidoRepository pedidoRepository;
    private final HistoricoStatusPedidoRepository historicoStatusPedidoRepository;
    private final com.glowup.api.repository.PedidoEnderecoRepository pedidoEnderecoRepository;

    private PedidoClienteRespostaDTO resposta(com.glowup.api.domain.Pedido pedido) {
        var entregueEm = historicoStatusPedidoRepository
                .findFirstByPedidoIdAndStatusNovoOrderByOcorridoEmDesc(pedido.getId(), StatusPedido.ENTREGUE)
                .map(h -> h.getOcorridoEm()).orElse(null);
        var endereco = pedidoEnderecoRepository.findByPedidoId(pedido.getId()).orElse(null);
        return PedidoClienteRespostaDTO.de(pedido, numeroWhatsapp, entregueEm, prazoDevolucaoDias, endereco);
    }

    /** Número da revendedora para o link de retirada (RN09); vazio = sem link. */
    @org.springframework.beans.factory.annotation.Value("${glowup.whatsapp.numero:}")
    private String numeroWhatsapp = "";

    /** Mesmo prazo usado por DevolucaoService; evita divergência entre o limite exibido e o validado. */
    @org.springframework.beans.factory.annotation.Value("${glowup.devolucao.prazo-dias:7}")
    private long prazoDevolucaoDias = 7;

    @Transactional(readOnly = true)
    public List<PedidoClienteRespostaDTO> listarPedidos(Long clienteId) {
        return pedidoRepository.findByClienteIdOrderByDataCriacaoDesc(clienteId).stream()
                .map(this::resposta)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<PedidoClienteRespostaDTO> listarPedidos(Long clienteId, Pageable pageable) {
        return pedidoRepository.findByClienteId(clienteId, pageable).map(this::resposta);
    }

    @Transactional(readOnly = true)
    public PedidoClienteRespostaDTO buscarPedido(Long clienteId, Long pedidoId) {
        return pedidoRepository.findByIdAndClienteId(pedidoId, clienteId)
                .map(this::resposta)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido não encontrado."));
    }
}
