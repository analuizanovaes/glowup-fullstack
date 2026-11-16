package com.glowup.api.service;

import com.glowup.api.domain.ItemDevolucao;
import com.glowup.api.domain.ItemPedido;
import com.glowup.api.domain.MetodoPagamento;
import com.glowup.api.domain.Pagamento;
import com.glowup.api.domain.Pedido;
import com.glowup.api.domain.RegistroFinanceiro;
import com.glowup.api.dto.LucroRealDTO;
import com.glowup.api.repository.RegistroFinanceiroRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistroFinanceiroServiceTest {

    @Mock
    private RegistroFinanceiroRepository registroRepository;

    @InjectMocks
    private RegistroFinanceiroService registroFinanceiroService;

    @BeforeEach
    void configurarTaxasEstimadas() {
        ReflectionTestUtils.setField(registroFinanceiroService, "taxaPixPercentual", new BigDecimal("0.99"));
        ReflectionTestUtils.setField(registroFinanceiroService, "taxaCartaoPercentual", new BigDecimal("4.98"));
    }

    private Pedido criarPedido(BigDecimal custoFabrica, int quantidade) {
        ItemPedido item = new ItemPedido();
        item.setCustoFabrica(custoFabrica);
        item.setQuantidade(quantidade);

        Pedido pedido = new Pedido();
        pedido.setId(1L);
        pedido.setValorTotal(new BigDecimal("100.00"));
        pedido.setItens(List.of(item));
        return pedido;
    }

    private Pagamento criarPagamento(MetodoPagamento metodo, BigDecimal taxaGateway) {
        return Pagamento.builder()
                .metodo(metodo)
                .valor(new BigDecimal("100.00"))
                .taxaGateway(taxaGateway)
                .build();
    }

    private RegistroFinanceiro registrar(Pedido pedido, Pagamento pagamento) {
        when(registroRepository.findByPedidoId(1L)).thenReturn(Optional.empty());
        when(registroRepository.save(any(RegistroFinanceiro.class))).thenAnswer(chamada -> chamada.getArgument(0));
        return registroFinanceiroService.registrar(pedido, pagamento);
    }

    @Test
    @DisplayName("Lucro Real = valor da venda - custo de fábrica - taxa informada pelo gateway")
    void deveCalcularLucroRealComTaxaDoGateway() {
        RegistroFinanceiro registro = registrar(criarPedido(new BigDecimal("40.00"), 1),
                criarPagamento(MetodoPagamento.PIX, new BigDecimal("0.99")));

        assertTrue(registro.isDadosSuficientes());
        assertEquals(0, new BigDecimal("59.01").compareTo(registro.getLucroReal()));
    }

    @Test
    @DisplayName("O custo de fábrica é multiplicado pela quantidade de cada item")
    void deveMultiplicarCustoPelaQuantidade() {
        RegistroFinanceiro registro = registrar(criarPedido(new BigDecimal("40.00"), 2),
                criarPagamento(MetodoPagamento.PIX, new BigDecimal("0.99")));

        assertEquals(0, new BigDecimal("80.00").compareTo(registro.getCustoFabrica()));
        assertEquals(0, new BigDecimal("19.01").compareTo(registro.getLucroReal()));
    }

    @Test
    @DisplayName("Sem taxa do gateway, estima pelo percentual configurado do cartão")
    void deveEstimarTaxaQuandoGatewayNaoInforma() {
        RegistroFinanceiro registro = registrar(criarPedido(new BigDecimal("40.00"), 1),
                criarPagamento(MetodoPagamento.CARTAO, null));

        assertEquals(0, new BigDecimal("4.98").compareTo(registro.getTaxaGateway()));
        assertEquals(0, new BigDecimal("55.02").compareTo(registro.getLucroReal()));
    }

    @Test
    @DisplayName("Sem custo de fábrica, grava 'dados insuficientes' em vez de inventar o lucro")
    void deveMarcarDadosInsuficientesSemCusto() {
        RegistroFinanceiro registro = registrar(criarPedido(null, 1),
                criarPagamento(MetodoPagamento.PIX, new BigDecimal("0.99")));

        assertFalse(registro.isDadosSuficientes());
        assertNull(registro.getLucroReal());
    }

    @Test
    @DisplayName("Pedido sem itens também resulta em dados insuficientes")
    void deveMarcarDadosInsuficientesSemItens() {
        Pedido pedido = criarPedido(new BigDecimal("40.00"), 1);
        pedido.setItens(List.of());

        RegistroFinanceiro registro = registrar(pedido, criarPagamento(MetodoPagamento.PIX, new BigDecimal("0.99")));

        assertFalse(registro.isDadosSuficientes());
        assertNull(registro.getLucroReal());
    }

    @Test
    @DisplayName("Devolução parcial tira da apuração a venda estornada, o custo devolvido e a taxa proporcional")
    void deveAjustarLucroNaDevolucaoParcial() {
        RegistroFinanceiro registro = RegistroFinanceiro.builder()
                .valorVenda(new BigDecimal("100.00"))
                .custoFabrica(new BigDecimal("40.00"))
                .taxaGateway(new BigDecimal("1.00"))
                .lucroReal(new BigDecimal("59.00"))
                .dadosSuficientes(true)
                .build();
        when(registroRepository.findByPedidoId(1L)).thenReturn(Optional.of(registro));

        ItemPedido itemPedido = new ItemPedido();
        itemPedido.setCustoFabrica(new BigDecimal("20.00"));
        ItemDevolucao devolvido = ItemDevolucao.builder().itemPedido(itemPedido).quantidade(1)
                .produtoLacrado(true).build();

        registroFinanceiroService.ajustarParaDevolucaoParcial(1L, new BigDecimal("50.00"), List.of(devolvido));

        assertEquals(0, new BigDecimal("50.00").compareTo(registro.getValorVenda()));
        assertEquals(0, new BigDecimal("20.00").compareTo(registro.getCustoFabrica()));
        assertEquals(0, new BigDecimal("0.50").compareTo(registro.getTaxaGateway()));
        assertEquals(0, new BigDecimal("29.50").compareTo(registro.getLucroReal()));
        assertTrue(registro.isDadosSuficientes());
    }

    @Test
    @DisplayName("É idempotente: um pedido já apurado não gera novo registro")
    void naoDeveDuplicarRegistroDoMesmoPedido() {
        RegistroFinanceiro existente = RegistroFinanceiro.builder().build();
        when(registroRepository.findByPedidoId(1L)).thenReturn(Optional.of(existente));

        RegistroFinanceiro resultado = registroFinanceiroService.registrar(criarPedido(new BigDecimal("40.00"), 1),
                criarPagamento(MetodoPagamento.PIX, new BigDecimal("0.99")));

        assertSame(existente, resultado);
        verify(registroRepository, never()).save(any());
    }

    @Test
    @DisplayName("Consulta de lucro sem registro retorna 'Dados Insuficientes'")
    void deveInformarDadosInsuficientesSemRegistro() {
        when(registroRepository.findByPedidoId(1L)).thenReturn(Optional.empty());

        LucroRealDTO lucro = registroFinanceiroService.consultarLucro(1L);

        assertEquals(LucroRealDTO.DADOS_INSUFICIENTES, lucro.observacao());
        assertNull(lucro.lucroReal());
    }
}
