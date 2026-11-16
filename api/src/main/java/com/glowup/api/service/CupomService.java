package com.glowup.api.service;

import com.glowup.api.domain.Cupom;
import com.glowup.api.domain.Pedido;
import com.glowup.api.dto.CupomRequisicaoDTO;
import com.glowup.api.exception.RecursoNaoEncontradoException;
import com.glowup.api.repository.CupomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.text.NumberFormat;

/** Validação e cálculo de cupons no checkout (seção 6.34) e CRUD administrativo. */
@Service
@RequiredArgsConstructor
public class CupomService {

    private static final BigDecimal CEM = new BigDecimal("100");

    private final CupomRepository cupomRepository;

    /** Resultado da aplicação: o cupom usado e o valor a deduzir do subtotal. */
    public record DescontoAplicado(Cupom cupom, BigDecimal valor) {}

    /**
     * Valida o cupom contra o subtotal calculado no servidor, consome um uso de forma atômica e devolve
     * o desconto (nunca maior que o subtotal). Falhas viram 400 com mensagem clara para a cliente.
     */
    @Transactional
    public DescontoAplicado validarEConsumir(String codigo, BigDecimal subtotal) {
        Cupom cupom = validar(codigo, subtotal);
        if (cupomRepository.registrarUso(cupom.getId()) == 0) {
            throw new IllegalArgumentException("Este cupom atingiu o limite de usos.");
        }
        return new DescontoAplicado(cupom, calcularDesconto(cupom, subtotal));
    }

    @Transactional(readOnly = true)
    public DescontoAplicado simular(String codigo, BigDecimal subtotal) {
        Cupom cupom = validar(codigo, subtotal);
        if (cupom.getLimiteUso() != null && cupom.getUsosRealizados() >= cupom.getLimiteUso()) {
            throw new IllegalArgumentException("Este cupom atingiu o limite de usos.");
        }
        return new DescontoAplicado(cupom, calcularDesconto(cupom, subtotal));
    }

    private Cupom validar(String codigo, BigDecimal subtotal) {
        if (codigo == null || codigo.isBlank()) {
            throw new IllegalArgumentException("Cupom inválido ou não encontrado.");
        }
        Cupom cupom = cupomRepository.findByCodigoIgnoreCase(normalizarCodigo(codigo))
                .orElseThrow(() -> new IllegalArgumentException("Cupom inválido ou não encontrado."));

        LocalDateTime agora = LocalDateTime.now();
        boolean vigente = cupom.isAtivo()
                && !agora.isBefore(cupom.getValidadeInicio())
                && !agora.isAfter(cupom.getValidadeFim());
        if (!vigente) {
            throw new IllegalArgumentException("Este cupom está expirado ou inativo.");
        }

        BigDecimal minimo = cupom.getValorMinimo() == null ? BigDecimal.ZERO : cupom.getValorMinimo();
        if (subtotal.compareTo(minimo) < 0) {
            throw new IllegalArgumentException("Este cupom exige um pedido mínimo de "
                    + NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(minimo) + ".");
        }

        if (cupom.getLimiteUso() != null && cupom.getUsosRealizados() >= cupom.getLimiteUso()) {
            throw new IllegalArgumentException("Este cupom atingiu o limite de usos.");
        }
        return cupom;
    }

    @Transactional
    public void liberarUso(Pedido pedido) {
        if (pedido != null && pedido.getCupom() != null) {
            cupomRepository.liberarUso(pedido.getCupom().getId());
        }
    }

    BigDecimal calcularDesconto(Cupom cupom, BigDecimal subtotal) {
        BigDecimal desconto;
        if (cupom.getPercentualDesconto() != null) {
            desconto = subtotal.multiply(cupom.getPercentualDesconto()).divide(CEM, 2, RoundingMode.HALF_UP);
        } else if (cupom.getValorDesconto() != null) {
            desconto = cupom.getValorDesconto();
        } else {
            desconto = BigDecimal.ZERO;
        }
        return desconto.min(subtotal).setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public List<Cupom> listar() {
        return cupomRepository.findAll();
    }

    @Transactional
    public Cupom criar(CupomRequisicaoDTO dto) {
        String codigo = normalizarCodigo(dto.codigo());
        if (cupomRepository.existsByCodigoIgnoreCase(codigo)) {
            throw new IllegalArgumentException("Já existe um cupom com o código " + codigo + ".");
        }
        Cupom cupom = new Cupom();
        cupom.setCodigo(codigo);
        aplicarDados(cupom, dto);
        return cupomRepository.save(cupom);
    }

    @Transactional
    public Cupom atualizar(Long id, CupomRequisicaoDTO dto) {
        Cupom cupom = cupomRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cupom não encontrado."));
        String codigo = normalizarCodigo(dto.codigo());
        if (!codigo.equals(cupom.getCodigo()) && cupomRepository.existsByCodigoIgnoreCase(codigo)) {
            throw new IllegalArgumentException("Já existe um cupom com o código " + codigo + ".");
        }
        cupom.setCodigo(codigo);
        aplicarDados(cupom, dto);
        return cupomRepository.save(cupom);
    }

    /** Exclusão lógica: o cupom permanece ligado aos pedidos antigos, apenas deixa de valer. */
    @Transactional
    public void desativar(Long id) {
        Cupom cupom = cupomRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cupom não encontrado."));
        cupom.setAtivo(false);
        cupomRepository.save(cupom);
    }

    private void aplicarDados(Cupom cupom, CupomRequisicaoDTO dto) {
        boolean temPercentual = dto.percentualDesconto() != null;
        boolean temValor = dto.valorDesconto() != null;
        if (temPercentual == temValor) {
            throw new IllegalArgumentException("Informe percentualDesconto OU valorDesconto (apenas um deles).");
        }
        if (temPercentual && dto.percentualDesconto().compareTo(CEM) > 0) {
            throw new IllegalArgumentException("O percentual de desconto não pode passar de 100.");
        }
        if (!dto.validadeFim().isAfter(dto.validadeInicio())) {
            throw new IllegalArgumentException("A data de fim deve ser posterior à data de início.");
        }
        cupom.setPercentualDesconto(dto.percentualDesconto());
        cupom.setValorDesconto(dto.valorDesconto());
        cupom.setValorMinimo(dto.valorMinimo() == null ? BigDecimal.ZERO : dto.valorMinimo());
        cupom.setValidadeInicio(dto.validadeInicio());
        cupom.setValidadeFim(dto.validadeFim());
        cupom.setLimiteUso(dto.limiteUso());
        cupom.setAtivo(dto.ativo() == null || dto.ativo());
    }

    private String normalizarCodigo(String codigo) {
        return codigo == null ? "" : codigo.trim().toUpperCase(Locale.ROOT);
    }
}
