package com.glowup.api.service;

import com.glowup.api.domain.AlertaValidade;
import com.glowup.api.domain.AnaliseReposicao;
import com.glowup.api.domain.Estoque;
import com.glowup.api.domain.ItemPedido;
import com.glowup.api.domain.ModalidadeProduto;
import com.glowup.api.domain.Pedido;
import com.glowup.api.domain.Produto;
import com.glowup.api.domain.StatusPedido;
import com.glowup.api.repository.AlertaValidadeRepository;
import com.glowup.api.repository.AnaliseReposicaoRepository;
import com.glowup.api.repository.EstoqueRepository;
import com.glowup.api.repository.ProdutoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Rotinas autônomas (CRON) do servidor, sempre no fuso America/Sao_Paulo:
 * RN08 virada de promoções (00:00), alerta de validade em 25 dias e CRM de reposição (manhã).
 * Cada rotina é idempotente: rodar duas vezes no mesmo dia não duplica alertas nem sugestões.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AutomacaoCronService {

    public static final int DIAS_ANTECEDENCIA_VALIDADE = 25;
    public static final String TIPO_ALERTA_VALIDADE = "ALERTA_VALIDADE";
    public static final String TIPO_REPOSICAO = "REPOSICAO_CRM";

    private static final List<StatusPedido> STATUS_COM_PAGAMENTO = List.of(
            StatusPedido.PAGO, StatusPedido.EM_SEPARACAO, StatusPedido.ENVIADO, StatusPedido.ENTREGUE);

    private final ProdutoRepository produtoRepository;
    private final EstoqueRepository estoqueRepository;
    private final AlertaValidadeRepository alertaValidadeRepository;
    private final AnaliseReposicaoRepository analiseReposicaoRepository;
    private final CrmAdminService crmAdminService;

    /** RN08: promoção expirada volta ao preço base; os pedidos antigos seguem congelados em itens_pedido. */
    @Scheduled(cron = "0 0 0 * * *", zone = "America/Sao_Paulo")
    @Transactional
    public int reverterPromocoesExpiradas() {
        List<Produto> expirados = produtoRepository
                .findByPrecoPromocionalIsNotNullAndDataLimitePromocaoBefore(LocalDateTime.now());
        for (Produto produto : expirados) {
            produto.setPrecoPromocional(null);
            produto.setDataInicioOferta(null);
            produto.setDataLimitePromocao(null);
            produtoRepository.save(produto);
        }
        log.info("CRON promoções: {} produto(s) revertido(s) ao preço base.", expirados.size());
        return expirados.size();
    }

    /** Alerta no painel (nunca para a cliente) de produtos a Pronta-Entrega vencendo em até 25 dias. */
    @Scheduled(cron = "0 0 8 * * *", zone = "America/Sao_Paulo")
    @Transactional
    public int verificarVencimentoProdutos() {
        LocalDate hoje = LocalDate.now();
        int criados = 0;
        for (Estoque estoque : estoqueRepository.buscarComValidade()) {
            Produto produto = estoque.getProduto();
            if (produto.getModalidade() != ModalidadeProduto.PRONTA_ENTREGA || estoque.getQuantidade() <= 0) {
                continue;
            }
            int dias = (int) ChronoUnit.DAYS.between(hoje, estoque.getDataValidade());
            if (dias > DIAS_ANTECEDENCIA_VALIDADE
                    || alertaValidadeRepository.existsByEstoqueIdAndDataPrevista(
                            estoque.getId(), estoque.getDataValidade())) {
                continue;
            }
            String mensagem = dias < 0
                    ? produto.getNome() + " está vencido desde " + estoque.getDataValidade() + "."
                    : produto.getNome() + " vence em " + dias + " dia(s), em " + estoque.getDataValidade() + ".";
            alertaValidadeRepository.save(AlertaValidade.builder()
                    .estoque(estoque)
                    .dataPrevista(estoque.getDataValidade())
                    .diasRestantes(dias)
                    .mensagem(mensagem)
                    .build());
            crmAdminService.criarNotificacao(TIPO_ALERTA_VALIDADE, "Validade próxima: " + produto.getNome(),
                    mensagem, null);
            criados++;
        }
        log.info("CRON validade: {} alerta(s) criado(s).", criados);
        return criados;
    }

    /** CRM: data da compra + tempo médio de uso = momento ideal para sugerir a reposição. */
    @Scheduled(cron = "0 30 8 * * *", zone = "America/Sao_Paulo")
    @Transactional
    public int verificarSugestaoReposicaoCRM() {
        LocalDate hoje = LocalDate.now();
        int criadas = 0;
        for (ItemPedido item : analiseReposicaoRepository.buscarItensSemAnalise(STATUS_COM_PAGAMENTO)) {
            Pedido pedido = item.getPedido();
            Produto produto = item.getProduto();
            if (pedido.getCliente() == null || produto == null || pedido.getDataCriacao() == null
                    || produto.getTempoMedioUsoDias() == null) {
                continue;
            }
            LocalDate dataCompra = pedido.getDataCriacao().toLocalDate();
            LocalDate dataReposicao = dataCompra.plusDays(produto.getTempoMedioUsoDias());
            if (dataReposicao.isAfter(hoje)) {
                continue;
            }
            analiseReposicaoRepository.save(AnaliseReposicao.builder()
                    .produto(produto)
                    .usuario(pedido.getCliente())
                    .pedidoId(pedido.getId())
                    .dataCompra(dataCompra)
                    .tempoMedioUsoDias(produto.getTempoMedioUsoDias())
                    .dataEstimadaReposicao(dataReposicao)
                    .quantidadeSugerida(BigDecimal.valueOf(item.getQuantidade()))
                    .build());
            crmAdminService.criarNotificacao(TIPO_REPOSICAO,
                    "Hora de contatar " + pedido.getCliente().getNome(),
                    "O " + produto.getNome() + " do pedido #" + pedido.getId() + " deve estar acabando. "
                            + "Sugira a reposição.",
                    pedido.getId());
            criadas++;
        }
        log.info("CRON CRM: {} sugestão(ões) de reposição criada(s).", criadas);
        return criadas;
    }
}
