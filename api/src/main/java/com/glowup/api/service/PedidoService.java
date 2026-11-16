package com.glowup.api.service;

import com.glowup.api.domain.Cupom;
import com.glowup.api.domain.ItemKit;
import com.glowup.api.domain.ItemCarrinho;
import com.glowup.api.domain.ItemPedido;
import com.glowup.api.domain.Kit;
import com.glowup.api.domain.ModalidadeProduto;
import com.glowup.api.domain.Pedido;
import com.glowup.api.domain.PedidoEndereco;
import com.glowup.api.domain.Produto;
import com.glowup.api.domain.StatusPedido;
import com.glowup.api.model.TermoAceite;
import com.glowup.api.domain.TipoEntrega;
import com.glowup.api.domain.TipoKit;
import com.glowup.api.domain.TipoMovimentacao;
import com.glowup.api.dto.CheckoutRequisicaoDTO;
import com.glowup.api.dto.CheckoutSimulacaoDTO;
import com.glowup.api.dto.CheckoutSimulacaoRespostaDTO;
import com.glowup.api.dto.CheckoutSimulacaoItemDTO;
import com.glowup.api.dto.ViaCepResponseDTO;
import com.glowup.api.exception.ConflitoDeEstadoException;
import com.glowup.api.exception.RecursoNaoEncontradoException;
import com.glowup.api.model.Endereco;
import com.glowup.api.model.Usuario;
import com.glowup.api.repository.EnderecoRepository;
import com.glowup.api.repository.EstoqueRepository;
import com.glowup.api.repository.PedidoEnderecoRepository;
import com.glowup.api.repository.PedidoRepository;
import com.glowup.api.repository.TermoAceiteRepository;
import com.glowup.api.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class PedidoService {

    private static final int IDADE_MINIMA = 18;
    /** Evita reservar novamente o mesmo carrinho enquanto há um pedido aguardando pagamento. */
    private static final int MAX_PEDIDOS_PENDENTES_POR_CLIENTE = 1;

    private final PedidoRepository pedidoRepository;
    private final PedidoEnderecoRepository pedidoEnderecoRepository;
    private final EnderecoRepository enderecoRepository;
    private final EstoqueRepository estoqueRepository;
    private final CarrinhoService carrinhoService;
    private final CupomService cupomService;
    private final LogisticaService logisticaService;
    private final EstoqueDisponibilidadeService disponibilidadeService;
    private final MovimentacaoEstoqueService movimentacaoEstoqueService;
    
    private final TermoAceiteRepository termoAceiteRepository;
    private final UsuarioRepository usuarioRepository;
    private final PlatformTransactionManager transactionManager;

    /** Versão vigente dos termos (glowup.termos.versao-atual): o aceite só vale para a versão que a cliente viu. */
    @org.springframework.beans.factory.annotation.Value("${glowup.termos.versao-atual:1.0}")
    private String versaoTermosAtual = "1.0";

    private record Entrega(TipoEntrega tipo, Long enderecoId, String pais, String cep, String logradouro,
                           String numero, String complemento, String bairro, String cidade, String uf,
                           BigDecimal frete, boolean conferirEndereco) {}

    private record BaseSimulacao(List<CheckoutSimulacaoItemDTO> itens, BigDecimal subtotal, int prazoDias) {}

    public CheckoutSimulacaoRespostaDTO simularCheckout(Usuario cliente, CheckoutSimulacaoDTO entrada) {
        CheckoutRequisicaoDTO dto = entrada.comoCheckout();
        if (dto.tipoEntrega() == null) throw new IllegalArgumentException("Informe o tipo de entrega.");
        TransactionTemplate leitura = new TransactionTemplate(transactionManager);
        leitura.setReadOnly(true);
        BaseSimulacao base = leitura.execute(status -> montarBaseSimulacao(cliente.getId()));
        if (base == null) throw new IllegalStateException("Não foi possível preparar a simulação do checkout.");
        BigDecimal desconto = BigDecimal.ZERO.setScale(2);
        if (dto.codigoCupom() != null && !dto.codigoCupom().isBlank()) {
            desconto = cupomService.simular(dto.codigoCupom(), base.subtotal()).valor();
        }
        // ViaCEP ocorre depois da transação de leitura, para não manter conexão do pool durante HTTP externo.
        ViaCepResponseDTO viaCep = consultarCepAntesDaTransacao(cliente, dto);
        Entrega entrega = resolverEntrega(cliente, dto, viaCep);
        BigDecimal total = base.subtotal().subtract(desconto).add(entrega.frete()).setScale(2, RoundingMode.HALF_UP);
        return new CheckoutSimulacaoRespostaDTO(base.itens(), base.subtotal(), desconto,
                entrega.frete(), total, base.prazoDias(), dto.codigoCupom());
    }

    private BaseSimulacao montarBaseSimulacao(Long clienteId) {
        List<ItemCarrinho> itens = carrinhoService.listarItensDoCarrinhoAtivo(clienteId);
        if (itens.isEmpty()) throw new IllegalArgumentException("O carrinho está vazio.");
        BigDecimal subtotal = BigDecimal.ZERO;
        List<CheckoutSimulacaoItemDTO> linhas = new ArrayList<>();
        List<ItemPedido> itensPrazo = new ArrayList<>();
        for (ItemCarrinho item : itens) {
            int disponivel = disponibilidadeService.calcular(item.getProduto());
            if (item.getQuantidade() > disponivel) {
                throw new ConflitoDeEstadoException("Estoque insuficiente para " + item.getProduto().getNome()
                        + ". Atualize o carrinho antes de concluir.");
            }
            BigDecimal preco = item.getProduto().obterPrecoVigente().setScale(2, RoundingMode.HALF_UP);
            BigDecimal linha = preco.multiply(BigDecimal.valueOf(item.getQuantidade()));
            subtotal = subtotal.add(linha);
            linhas.add(new CheckoutSimulacaoItemDTO(item.getProduto().getId(), item.getProduto().getNome(),
                    item.getQuantidade(), preco, linha));
            ItemPedido itemPedido = new ItemPedido();
            itemPedido.setProduto(item.getProduto());
            itensPrazo.add(itemPedido);
        }
        return new BaseSimulacao(linhas, subtotal.setScale(2, RoundingMode.HALF_UP),
                logisticaService.calcularPrazoUnificado(itensPrazo));
    }

    public Pedido finalizarCheckout(Usuario cliente, CheckoutRequisicaoDTO dto, String idempotencyKey) {
        validarMaioridade(cliente);
        Optional<Pedido> pedidoExistente = pedidoRepository.findByClienteIdAndIdempotencyKey(cliente.getId(), idempotencyKey);
        if (pedidoExistente.isPresent()) {
            return pedidoExistente.get();
        }

        ViaCepResponseDTO viaCep = consultarCepAntesDaTransacao(cliente, dto);
        return new TransactionTemplate(transactionManager).execute(status ->
                finalizarCheckoutTransacional(cliente, dto, idempotencyKey, viaCep));
    }

    private Pedido finalizarCheckoutTransacional(Usuario cliente, CheckoutRequisicaoDTO dto, String idempotencyKey,
                                                 ViaCepResponseDTO viaCep) {
        validarMaioridade(cliente);

        Optional<Pedido> pedidoExistente = pedidoRepository.findByClienteIdAndIdempotencyKey(cliente.getId(), idempotencyKey);
        if (pedidoExistente.isPresent()) {
            return pedidoExistente.get();
        }

        if (!Boolean.TRUE.equals(dto.aceitouTermos())) {
            throw new IllegalArgumentException("É necessário aceitar os termos e a política de privacidade.");
        }
        if (!versaoTermosAtual.equals(dto.versaoTermos().trim())) {
            throw new IllegalArgumentException(
                    "Os Termos de Uso foram atualizados. Recarregue a página, leia a versão vigente e aceite novamente.");
        }

        if (dto.tipoEntrega() == TipoEntrega.DOMICILIO) {
            String cepParaValidacao = dto.cep();
            if (dto.enderecoId() != null) {
                cepParaValidacao = enderecoRepository.findByIdAndUsuarioId(dto.enderecoId(), cliente.getId())
                        .map(Endereco::getCep)
                        .orElse(dto.cep());
            }
                    
            if (cepParaValidacao != null && !cepParaValidacao.replaceAll("\\D", "").matches("\\d{8}")) {
                throw new IllegalArgumentException("O CEP informado é inválido: use 8 dígitos.");
            }
        }

        Entrega entrega = resolverEntrega(cliente, dto, viaCep);

        // ViaCEP pode demorar; só adquire o lock do cliente depois da chamada externa. Reconfere sob lock
        // para manter idempotência e o limite de pedidos pendentes seguros contra checkouts concorrentes.
        usuarioRepository.bloquearParaCheckout(cliente.getId());
        pedidoExistente = pedidoRepository.findByClienteIdAndIdempotencyKey(cliente.getId(), idempotencyKey);
        if (pedidoExistente.isPresent()) {
            return pedidoExistente.get();
        }
        long pedidosPendentes = pedidoRepository.countByClienteIdAndStatus(cliente.getId(), StatusPedido.AGUARDANDO_PAGAMENTO);
        if (pedidosPendentes >= MAX_PEDIDOS_PENDENTES_POR_CLIENTE) {
            throw new ConflitoDeEstadoException(
                    "Você já tem um pedido aguardando pagamento. Conclua o pagamento ou aguarde a expiração para criar outro.");
        }
        TermoAceite termo = new TermoAceite();
        termo.setUsuario(cliente);
        termo.setDataHoraAceite(LocalDateTime.now());
        termo.setTipoAceite("CHECKOUT_PEDIDO");
        termo.setVersaoDocumento(versaoTermosAtual);
        termoAceiteRepository.save(termo);

        List<ItemCarrinho> itensCarrinho = carrinhoService.listarItensDoCarrinhoAtivo(cliente.getId());
        if (itensCarrinho.isEmpty()) {
            throw new IllegalArgumentException("O carrinho está vazio.");
        }

        List<ItemPedido> itens = new ArrayList<>();
        Map<Long, Integer> baixas = new TreeMap<>();
        Map<Long, Produto> produtosBaixados = new LinkedHashMap<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        for (ItemCarrinho itemCarrinho : itensCarrinho) {
            Produto produto = itemCarrinho.getProduto();
            int quantidade = itemCarrinho.getQuantidade();
            BigDecimal precoPraticado = produto.obterPrecoVigente().setScale(2, RoundingMode.HALF_UP);

            ItemPedido item = new ItemPedido();
            item.setProduto(produto);
            item.setQuantidade(quantidade);
            item.setPrecoUnitario(precoPraticado);
            item.setCustoFabrica(produto.getCustoFabrica());
            item.setSubtotal(precoPraticado.multiply(BigDecimal.valueOf(quantidade)));
            itens.add(item);
            subtotal = subtotal.add(item.getSubtotal());

            acumularBaixas(produto, quantidade, baixas, produtosBaixados);
        }

        baixarEstoque(baixas, produtosBaixados);

        BigDecimal desconto = BigDecimal.ZERO.setScale(2);
        Cupom cupom = null;
        String codigoCupom = dto.codigoCupom();
        
        if (codigoCupom != null && !codigoCupom.isBlank()) {
            CupomService.DescontoAplicado aplicado = cupomService.validarEConsumir(codigoCupom, subtotal);
            cupom = aplicado.cupom();
            desconto = aplicado.valor();
        }

        int prazo = logisticaService.calcularPrazoUnificado(itens);
        BigDecimal total = subtotal.subtract(desconto).add(entrega.frete());
        if (total.signum() <= 0) {
            throw new IllegalArgumentException("O total do pedido deve ser maior que zero para concluir o checkout.");
        }

        Pedido pedido = new Pedido();
        
        pedido.setIdempotencyKey(idempotencyKey);
        
        pedido.setCliente(cliente);
        pedido.setStatus(StatusPedido.AGUARDANDO_PAGAMENTO);
        pedido.setTipoEntrega(entrega.tipo());
        pedido.setPrazoDias(prazo);
        pedido.setSubtotal(subtotal);
        pedido.setDescontoCupom(desconto);
        pedido.setValorFrete(entrega.frete());
        pedido.setValorTotal(total);
        pedido.setCupom(cupom);
        pedido.setCepEntrega(entrega.cep());
        pedido.setBairroEntrega(entrega.bairro());
        pedido.setConferenciaEnderecoPendente(entrega.conferirEndereco());
        for (ItemPedido item : itens) {
            item.setPedido(pedido);
        }
        pedido.setItens(itens);

        Pedido salvo = pedidoRepository.save(pedido);
        pedidoEnderecoRepository.save(montarPedidoEndereco(salvo.getId(), entrega, prazo));
        registrarSaidas(baixas, produtosBaixados, salvo.getId());
        return salvo;
    }

    private ViaCepResponseDTO consultarCepAntesDaTransacao(Usuario cliente, CheckoutRequisicaoDTO dto) {
        if (dto.tipoEntrega() != TipoEntrega.DOMICILIO) return null;
        String cep = dto.cep();
        if (dto.enderecoId() != null) {
            cep = enderecoRepository.findByIdAndUsuarioId(dto.enderecoId(), cliente.getId())
                    .map(Endereco::getCep)
                    .orElse(dto.cep());
        }
        if (cep == null || !cep.replaceAll("\\D", "").matches("\\d{8}")) return null;
        try {
            return logisticaService.buscarCepComFallback(cep);
        } catch (RuntimeException falha) {
            ViaCepResponseDTO fallback = new ViaCepResponseDTO();
            fallback.setErroConexao(true);
            log.warn("ViaCEP indisponível antes do checkout; o pedido exigirá conferência manual: {}", falha.getMessage());
            return fallback;
        }
    }

    private void validarMaioridade(Usuario cliente) {
        if (cliente.calcularIdade() < IDADE_MINIMA) {
            throw new IllegalArgumentException("A compra não é permitida para menores de 18 anos.");
        }
    }

    private Entrega resolverEntrega(Usuario cliente, CheckoutRequisicaoDTO dto, ViaCepResponseDTO viaCep) {
        if (dto.tipoEntrega() == TipoEntrega.RETIRADA) {
            return new Entrega(TipoEntrega.RETIRADA, null, "Brasil", null, null, null, null, null, null, null,
                    BigDecimal.ZERO.setScale(2), false);
        }

        Long enderecoId = null;
        String pais = dto.pais() == null || dto.pais().isBlank() ? "Brasil" : dto.pais().trim();
        String cep = limpar(dto.cep());
        String logradouro = limpar(dto.logradouro());
        String numero = limpar(dto.numero());
        String complemento = limpar(dto.complemento());
        String bairro = limpar(dto.bairro());
        String cidade = limpar(dto.cidade());
        String uf = limpar(dto.uf());
        boolean conferirEndereco = false;

        if (dto.enderecoId() != null) {
            Endereco salvo = enderecoRepository.findByIdAndUsuarioId(dto.enderecoId(), cliente.getId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Endereço não encontrado."));
            enderecoId = salvo.getId();
            pais = salvo.getPais();
            cep = salvo.getCep();
            logradouro = salvo.getLogradouro();
            numero = salvo.getNumero();
            complemento = salvo.getComplemento();
            bairro = salvo.getBairro();
            cidade = salvo.getCidade();
            uf = salvo.getUf();
        }

        if (viaCep == null || viaCep.isErroConexao()) {
            // Sem resposta do ViaCEP, aceita o endereço informado e sinaliza conferência manual do pedido.
            conferirEndereco = true;
        } else {
            if (Boolean.TRUE.equals(viaCep.getErro())) {
                throw new RecursoNaoEncontradoException("O CEP informado não existe nos Correios.");
            }
            // O ViaCEP também valida endereços salvos, evitando que dados editáveis alterem a região do frete.
            bairro = preferirOficial(viaCep.getBairro(), bairro);
            uf = preferirOficial(viaCep.getUf(), uf);
            cidade = preferirOficial(viaCep.getLocalidade(), cidade);
            logradouro = preferirOficial(viaCep.getLogradouro(), logradouro);
        }

        if (cep == null || logradouro == null || numero == null || bairro == null || cidade == null || uf == null) {
            throw new IllegalArgumentException(
                    "Para entrega em domicílio informe o endereço completo (cep, logradouro, número, bairro, cidade e uf) "
                            + "ou escolha a Retirada Grátis.");
        }

        BigDecimal frete = logisticaService.calcularFrete(pais, uf, bairro).setScale(2, RoundingMode.HALF_UP);
        return new Entrega(TipoEntrega.DOMICILIO, enderecoId, "Brasil", cep, logradouro, numero, complemento,
                bairro, cidade, uf.toUpperCase(Locale.ROOT), frete, conferirEndereco);
    }

    private PedidoEndereco montarPedidoEndereco(Long pedidoId, Entrega entrega, int prazo) {
        return PedidoEndereco.builder()
                .pedidoId(pedidoId)
                .enderecoId(entrega.enderecoId())
                .pais(entrega.pais())
                .cep(entrega.cep())
                .logradouro(entrega.logradouro())
                .numero(entrega.numero())
                .complemento(entrega.complemento())
                .bairro(entrega.bairro())
                .cidade(entrega.cidade())
                .uf(entrega.uf())
                .tipoEntrega(entrega.tipo())
                .prazoDias(prazo)
                .build();
    }

    private void acumularBaixas(Produto produto, int quantidade, Map<Long, Integer> baixas,
                                Map<Long, Produto> produtosBaixados) {
        if (produto instanceof Kit kit && kit.getTipo() == TipoKit.MANUAL) {
            for (ItemKit componente : kit.getItens()) {
                if (componente.getProduto() == null) {
                    throw new ConflitoDeEstadoException("O kit '" + kit.getNome()
                            + "' contém um componente inativo. Atualize o kit antes de concluir a compra.");
                }
                acumular(componente.getProduto(), componente.getQuantidade() * quantidade, baixas, produtosBaixados);
            }
            return;
        }
        acumular(produto, quantidade, baixas, produtosBaixados);
    }

    private void acumular(Produto produto, int quantidade, Map<Long, Integer> baixas,
                          Map<Long, Produto> produtosBaixados) {
        if (produto.getModalidade() == ModalidadeProduto.SOB_ENCOMENDA) {
            return;
        }
        baixas.merge(produto.getId(), quantidade, Integer::sum);
        produtosBaixados.put(produto.getId(), produto);
    }

    private void baixarEstoque(Map<Long, Integer> baixas, Map<Long, Produto> produtosBaixados) {
        LocalDateTime agora = LocalDateTime.now();
        for (Map.Entry<Long, Integer> baixa : baixas.entrySet()) {
            int linhas = estoqueRepository.baixarSaldo(baixa.getKey(), baixa.getValue(), agora);
            if (linhas == 0) {
                Produto produto = produtosBaixados.get(baixa.getKey());
                String nome = produto != null ? produto.getNome() : "um dos itens";
                throw new ConflitoDeEstadoException("Estoque insuficiente para " + nome
                        + ". Atualize o carrinho e tente novamente.");
            }
        }
    }

    private void registrarSaidas(Map<Long, Integer> baixas, Map<Long, Produto> produtosBaixados, Long pedidoId) {
        for (Map.Entry<Long, Integer> baixa : baixas.entrySet()) {
            movimentacaoEstoqueService.registrar(produtosBaixados.get(baixa.getKey()), TipoMovimentacao.SAIDA,
                    baixa.getValue(), pedidoId, "Reserva efetiva no checkout do pedido #" + pedidoId);
        }
    }

    private String preferirOficial(String oficial, String digitado) {
        return oficial == null || oficial.isBlank() ? digitado : oficial.trim();
    }

    private String limpar(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
