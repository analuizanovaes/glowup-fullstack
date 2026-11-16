package com.glowup.api.service;

import com.glowup.api.domain.ItemDevolucao;
import com.glowup.api.domain.ItemPedido;
import com.glowup.api.domain.MetodoPagamento;
import com.glowup.api.domain.Pagamento;
import com.glowup.api.domain.Pedido;
import com.glowup.api.domain.RegistroFinanceiro;
import com.glowup.api.dto.LucroRealDTO;
import com.glowup.api.repository.RegistroFinanceiroRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RegistroFinanceiroService {

    private static final BigDecimal CEM = new BigDecimal("100");

    private final RegistroFinanceiroRepository registroRepository;

    @Value("${glowup.financeiro.taxa-pix-percentual:0.99}")
    private BigDecimal taxaPixPercentual;

    @Value("${glowup.financeiro.taxa-cartao-percentual:4.98}")
    private BigDecimal taxaCartaoPercentual;

    /**
     * Lucro Real = valorVenda - custoFabrica - taxasFinanceiras (RN05).
     * valorVenda é o total do pedido pago (itens, frete e desconto de cupom). Juros de parcelamento
     * são cobrados da compradora e, por isso, não entram como custo nem como receita.
     * Se faltar custo de fábrica ou taxa, o registro é gravado sem lucro para não distorcer métricas.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public RegistroFinanceiro registrar(Pedido pedido, Pagamento pagamento) {
        Optional<RegistroFinanceiro> existente = registroRepository.findByPedidoId(pedido.getId());
        if (existente.isPresent()) {
            return existente.get();
        }

        BigDecimal valorVenda = pedido.getValorTotal();
        BigDecimal custoTotal = calcularCustoTotal(pedido.getItens());
        BigDecimal taxas = obterTaxasFinanceiras(pagamento);
        // A taxa é estimada quando o Mercado Pago não informou o valor líquido (pagamento.getTaxaGateway() == null)
        // e, por isso, ela foi calculada pelo percentual configurável do método de pagamento.
        // A flag é gravada junto com o registro, que fica congelado após a aprovação (RN05).
        boolean taxaEstimada = pagamento.getTaxaGateway() == null && taxas != null;
        BigDecimal lucroReal = calcularLucroReal(valorVenda, custoTotal, taxas);

        RegistroFinanceiro registro = RegistroFinanceiro.builder()
                .pedido(pedido)
                .valorVenda(valorVenda)
                .custoFabrica(custoTotal)
                .taxaGateway(taxas)
                .juros(pagamento.getJuros())
                .lucroReal(lucroReal)
                .dadosSuficientes(lucroReal != null)
                .estornado(false)
                .taxaEstimada(taxaEstimada)
                .build();
        return registroRepository.save(registro);
    }

    /**
     * Devolução parcial recebida: tira da apuração o valor estornado à cliente, o custo de fábrica das unidades
     * que voltaram à prateleira e a parte proporcional da taxa do gateway, e recalcula o lucro real.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void ajustarParaDevolucaoParcial(Long pedidoId, BigDecimal valorEstornado, List<ItemDevolucao> itensDevolvidos) {
        registroRepository.findByPedidoId(pedidoId).ifPresent(registro -> {
            BigDecimal vendaAtual = registro.getValorVenda();
            BigDecimal novaVenda = vendaAtual.subtract(valorEstornado == null ? BigDecimal.ZERO : valorEstornado)
                    .max(BigDecimal.ZERO);

            if (registro.getTaxaGateway() != null && vendaAtual.signum() > 0) {
                BigDecimal proporcao = novaVenda.divide(vendaAtual, 10, RoundingMode.HALF_UP);
                registro.setTaxaGateway(registro.getTaxaGateway().multiply(proporcao).setScale(2, RoundingMode.HALF_UP));
            }

            BigDecimal custoDevolvido = calcularCustoDevolvido(itensDevolvidos);
            if (registro.getCustoFabrica() != null && custoDevolvido != null) {
                registro.setCustoFabrica(registro.getCustoFabrica().subtract(custoDevolvido).max(BigDecimal.ZERO));
            }

            registro.setValorVenda(novaVenda);
            registro.setLucroReal(calcularLucroReal(novaVenda, registro.getCustoFabrica(), registro.getTaxaGateway()));
            registro.setDadosSuficientes(registro.getLucroReal() != null);
            registroRepository.save(registro);
        });
    }

    private BigDecimal calcularCustoDevolvido(List<ItemDevolucao> itens) {
        if (itens == null || itens.isEmpty()) {
            return null;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (ItemDevolucao item : itens) {
            if (!Boolean.TRUE.equals(item.getProdutoLacrado())) continue;
            BigDecimal custoUnitario = item.getItemPedido().getCustoFabrica();
            if (custoUnitario == null || item.getQuantidade() == null) {
                return null;
            }
            total = total.add(custoUnitario.multiply(BigDecimal.valueOf(item.getQuantidade())));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void marcarComoEstornado(Long pedidoId) {
        registroRepository.findByPedidoId(pedidoId).ifPresent(registro -> {
            registro.setEstornado(true);
            registroRepository.save(registro);
        });
    }

    @Transactional(readOnly = true)
    public LucroRealDTO consultarLucro(Long pedidoId) {
        Optional<RegistroFinanceiro> registroOpt = registroRepository.findByPedidoId(pedidoId);
        
        if (registroOpt.isEmpty() || !registroOpt.get().isDadosSuficientes()) {
            return LucroRealDTO.indisponivel(LucroRealDTO.DADOS_INSUFICIENTES);
        }

        return paraLucroDTO(registroOpt.get());
    }

    @Transactional(readOnly = true)
    public java.util.Map<Long, LucroRealDTO> consultarLucros(List<Long> pedidoIds) {
        if (pedidoIds == null || pedidoIds.isEmpty()) return java.util.Map.of();
        return registroRepository.buscarPorPedidos(pedidoIds).stream()
                .collect(java.util.stream.Collectors.toMap(r -> r.getPedido().getId(), r ->
                        r.isDadosSuficientes() ? paraLucroDTO(r)
                                : LucroRealDTO.indisponivel(LucroRealDTO.DADOS_INSUFICIENTES)));
    }

    private LucroRealDTO paraLucroDTO(RegistroFinanceiro registro) {

        // A taxa só é "estimada" se, na aprovação do pagamento, o Mercado Pago não enviou o valor oficial
        // (valor líquido) e a taxa foi calculada pelo percentual configurado. Essa informação foi gravada em
        // registro.isTaxaEstimada() no momento do registro; como o RegistroFinanceiro é congelado após a
        // aprovação (RN05), a flag não muda depois, então basta repassá-la ao painel em vez de fixar true/false.
        return new LucroRealDTO(
                registro.getValorVenda(),
                registro.getCustoFabrica(),
                registro.getTaxaGateway(),
                registro.getLucroReal(),
                null,
                registro.isTaxaEstimada()
        );
    }

    private BigDecimal calcularLucroReal(BigDecimal valorVenda, BigDecimal custoTotal, BigDecimal taxas) {
        if (valorVenda == null || custoTotal == null || taxas == null) {
            return null;
        }
        return valorVenda.subtract(custoTotal).subtract(taxas);
    }

    private BigDecimal calcularCustoTotal(List<ItemPedido> itens) {
        if (itens == null || itens.isEmpty()) {
            return null;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (ItemPedido item : itens) {
            if (item.getCustoFabrica() == null || item.getQuantidade() == null) {
                return null;
            }
            total = total.add(item.getCustoFabrica().multiply(BigDecimal.valueOf(item.getQuantidade())));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Prioriza o valor líquido informado pelo Mercado Pago; na ausência dele, estima a taxa pelo
     * percentual configurável do método de pagamento.
     */
    private BigDecimal obterTaxasFinanceiras(Pagamento pagamento) {
        if (pagamento.getTaxaGateway() != null) {
            return pagamento.getTaxaGateway();
        }
        if (pagamento.getValor() == null) {
            return null;
        }
        BigDecimal percentual = pagamento.getMetodo() == MetodoPagamento.PIX ? taxaPixPercentual : taxaCartaoPercentual;
        return pagamento.getValor().multiply(percentual).divide(CEM, 2, RoundingMode.HALF_UP);
    }
}
