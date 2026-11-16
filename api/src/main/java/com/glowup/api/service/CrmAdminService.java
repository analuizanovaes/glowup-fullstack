package com.glowup.api.service;

import com.glowup.api.domain.AlertaValidade;
import com.glowup.api.domain.AnaliseReposicao;
import com.glowup.api.domain.NotificacaoAdmin;
import com.glowup.api.domain.StatusAlertaValidade;
import com.glowup.api.domain.StatusReposicao;
import com.glowup.api.dto.AlertaValidadeRespostaDTO;
import com.glowup.api.dto.AnaliseReposicaoRespostaDTO;
import com.glowup.api.dto.NotificacaoAdminRespostaDTO;
import com.glowup.api.exception.RecursoNaoEncontradoException;
import com.glowup.api.repository.AlertaValidadeRepository;
import com.glowup.api.repository.AnaliseReposicaoRepository;
import com.glowup.api.repository.NotificacaoAdminRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/** Painel da administradora: alertas de validade, sugestões de reposição do CRM e notificações. */
@Service
@RequiredArgsConstructor
public class CrmAdminService {

    public static final String TIPO_NOVA_VENDA = "NOVA_VENDA";

    private final AlertaValidadeRepository alertaValidadeRepository;
    private final AnaliseReposicaoRepository analiseReposicaoRepository;
    private final NotificacaoAdminRepository notificacaoAdminRepository;

    @Transactional(readOnly = true)
    public List<AlertaValidadeRespostaDTO> listarAlertasAbertos() {
        return alertaValidadeRepository.listarPorStatus(StatusAlertaValidade.ABERTO).stream()
                .map(AlertaValidadeRespostaDTO::de)
                .toList();
    }

    @Transactional
    public void atualizarAlerta(Long id, StatusAlertaValidade novoStatus) {
        AlertaValidade alerta = alertaValidadeRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Alerta não encontrado."));
        alerta.setStatus(novoStatus);
        alerta.setResolvidoEm(novoStatus == StatusAlertaValidade.RESOLVIDO ? LocalDateTime.now() : null);
        alertaValidadeRepository.save(alerta);
    }

    @Transactional(readOnly = true)
    public List<AnaliseReposicaoRespostaDTO> listarReposicoes(StatusReposicao status) {
        return analiseReposicaoRepository.listarPorStatus(status).stream()
                .map(AnaliseReposicaoRespostaDTO::de)
                .toList();
    }

    @Transactional
    public void atualizarReposicao(Long id, StatusReposicao novoStatus) {
        AnaliseReposicao analise = analiseReposicaoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Sugestão de reposição não encontrada."));
        analise.setStatus(novoStatus);
        analiseReposicaoRepository.save(analise);
    }

    @Transactional(readOnly = true)
    public List<NotificacaoAdminRespostaDTO> listarNotificacoesNaoLidas() {
        return notificacaoAdminRepository.findByVisualizadaFalseOrderByCriadaEmDesc().stream()
                .map(NotificacaoAdminRespostaDTO::de)
                .toList();
    }

    @Transactional
    public void marcarNotificacaoVisualizada(Long id) {
        NotificacaoAdmin notificacao = notificacaoAdminRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Notificação não encontrada."));
        notificacao.setVisualizada(true);
        notificacaoAdminRepository.save(notificacao);
    }

    @Transactional
    public NotificacaoAdmin criarNotificacao(String tipo, String titulo, String mensagem, Long pedidoId) {
        return notificacaoAdminRepository.save(NotificacaoAdmin.builder()
                .tipo(tipo)
                .titulo(titulo)
                .mensagem(mensagem)
                .pedidoId(pedidoId)
                .build());
    }

    /** Transação própria: roda após o commit da aprovação do pagamento (alerta de nova venda no painel). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarNovaVenda(Long pedidoId, String cliente, BigDecimal total) {
        criarNotificacao(TIPO_NOVA_VENDA,
                "Nova venda: pedido #" + pedidoId,
                String.format(Locale.of("pt", "BR"), "%s pagou R$ %,.2f.", cliente, total),
                pedidoId);
    }
}
