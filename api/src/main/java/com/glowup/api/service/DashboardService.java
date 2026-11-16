package com.glowup.api.service;

import com.glowup.api.domain.StatusPedido;
import com.glowup.api.dto.ResumoDashboardDTO;
import com.glowup.api.repository.PedidoRepository;
import com.glowup.api.repository.RegistroFinanceiroRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final RegistroFinanceiroRepository registroRepository;
    private final PedidoRepository pedidoRepository;

    /**
     * Pedidos com dados insuficientes entram no faturamento e na contagem de vendas, mas ficam fora
     * de custo, taxas e lucro, para que o Lucro Real não seja distorcido.
     */
    @Transactional(readOnly = true)
    public ResumoDashboardDTO gerarResumo(LocalDate inicio, LocalDate fim) {
        LocalDate dataInicio = inicio != null ? inicio : LocalDate.now().withDayOfMonth(1);
        LocalDate dataFim = fim != null ? fim : LocalDate.now();
        if (dataFim.isBefore(dataInicio)) {
            throw new IllegalArgumentException("A data final não pode ser anterior à data inicial.");
        }

        LocalDateTime de = dataInicio.atStartOfDay();
        LocalDateTime ate = dataFim.plusDays(1).atStartOfDay();

        return new ResumoDashboardDTO(
                dataInicio,
                dataFim,
                registroRepository.contarVendasNoPeriodo(de, ate),
                valorOuZero(registroRepository.somarFaturamentoNoPeriodo(de, ate)),
                valorOuZero(registroRepository.somarCustosNoPeriodo(de, ate)),
                valorOuZero(registroRepository.somarTaxasNoPeriodo(de, ate)),
                valorOuZero(registroRepository.somarLucroNoPeriodo(de, ate)),
                registroRepository.contarComDadosInsuficientesNoPeriodo(de, ate),
                contarPedidosPorStatus(de, ate));
    }

    private Map<StatusPedido, Long> contarPedidosPorStatus(LocalDateTime de, LocalDateTime ate) {
        Map<StatusPedido, Long> contagem = new EnumMap<>(StatusPedido.class);
        for (StatusPedido status : StatusPedido.values()) {
            contagem.put(status, pedidoRepository.contarPorStatusNoPeriodo(status, de, ate));
        }
        return contagem;
    }

    private BigDecimal valorOuZero(BigDecimal valor) {
        return valor != null ? valor : BigDecimal.ZERO;
    }
}
