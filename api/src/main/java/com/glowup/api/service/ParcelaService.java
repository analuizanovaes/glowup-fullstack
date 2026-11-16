package com.glowup.api.service;

import com.glowup.api.domain.MetodoPagamento;
import com.glowup.api.domain.Pagamento;
import com.glowup.api.domain.Parcela;
import com.glowup.api.domain.StatusParcela;
import com.glowup.api.repository.ParcelaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ParcelaService {

    private final ParcelaRepository parcelaRepository;

    /** Só o cartão gera parcelas. É idempotente: webhooks repetidos não duplicam registros. */
    @Transactional
    public void gerarParcelasDoCartao(Pagamento pagamento) {
        if (pagamento.getMetodo() != MetodoPagamento.CARTAO
                || parcelaRepository.existsByPagamentoId(pagamento.getId())) {
            return;
        }
        BigDecimal totalCobrado = pagamento.getValor().add(pagamento.getJuros());
        List<BigDecimal> valores = calcularValoresDasParcelas(totalCobrado, pagamento.getParcelas());
        parcelaRepository.saveAll(montarParcelas(pagamento, valores));
    }

    @Transactional
    public void marcarComoEstornadas(Pagamento pagamento) {
        parcelaRepository.findByPagamentoIdOrderByNumero(pagamento.getId())
                .forEach(parcela -> parcela.setStatus(StatusParcela.ESTORNADA));
    }

    /** A última parcela absorve a diferença de centavos, para a soma fechar exatamente no total. */
    static List<BigDecimal> calcularValoresDasParcelas(BigDecimal totalCobrado, int quantidade) {
        BigDecimal valorBase = totalCobrado.divide(BigDecimal.valueOf(quantidade), 2, RoundingMode.DOWN);
        List<BigDecimal> valores = new ArrayList<>();
        BigDecimal acumulado = BigDecimal.ZERO;
        for (int numero = 1; numero <= quantidade; numero++) {
            BigDecimal valor = numero == quantidade ? totalCobrado.subtract(acumulado) : valorBase;
            valores.add(valor);
            acumulado = acumulado.add(valor);
        }
        return valores;
    }

    private List<Parcela> montarParcelas(Pagamento pagamento, List<BigDecimal> valores) {
        LocalDate hoje = LocalDate.now();
        List<Parcela> parcelas = new ArrayList<>();
        for (int indice = 0; indice < valores.size(); indice++) {
            int numero = indice + 1;
            parcelas.add(Parcela.builder()
                    .pagamento(pagamento)
                    .numero(numero)
                    .valor(valores.get(indice))
                    .vencimento(hoje.plusMonths(numero))
                    .status(StatusParcela.PAGA)
                    .pagoEm(LocalDateTime.now())
                    .build());
        }
        return parcelas;
    }
}
