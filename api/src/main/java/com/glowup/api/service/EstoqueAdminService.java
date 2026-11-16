package com.glowup.api.service;

import com.glowup.api.domain.Estoque;
import com.glowup.api.domain.TipoMovimentacao;
import com.glowup.api.dto.EstoqueAjusteDTO;
import com.glowup.api.dto.EstoqueEntradaDTO;
import com.glowup.api.dto.EstoqueRespostaDTO;
import com.glowup.api.exception.ConflitoDeEstadoException;
import com.glowup.api.exception.RecursoNaoEncontradoException;
import com.glowup.api.repository.EstoqueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Controle físico da Pronta-Entrega pela administradora: entradas e contagens de inventário auditadas. */
@Service
@RequiredArgsConstructor
public class EstoqueAdminService {

    private final EstoqueRepository estoqueRepository;
    private final MovimentacaoEstoqueService movimentacaoEstoqueService;

    @Transactional(readOnly = true)
    public List<EstoqueRespostaDTO> listar() {
        return estoqueRepository.listarTodos().stream().map(EstoqueRespostaDTO::de).toList();
    }

    @Transactional
    public EstoqueRespostaDTO registrarEntrada(Long produtoId, EstoqueEntradaDTO dto) {
        Estoque estoque = buscarEstoqueParaAtualizacao(produtoId);
        estoqueRepository.adicionarSaldoAdmin(produtoId, dto.quantidade(), java.time.LocalDateTime.now());
        estoque = buscarEstoque(produtoId);
        if (dto.dataValidade() != null) {
            estoque.setDataValidade(dto.dataValidade());
        }
        Estoque salvo = estoqueRepository.save(estoque);
        movimentacaoEstoqueService.registrar(salvo.getProduto(), TipoMovimentacao.ENTRADA, dto.quantidade(), null,
                dto.observacao() != null ? dto.observacao() : "Entrada de estoque");
        return EstoqueRespostaDTO.de(salvo);
    }

    @Transactional
    public EstoqueRespostaDTO ajustarSaldo(Long produtoId, EstoqueAjusteDTO dto) {
        Estoque estoque = buscarEstoqueParaAtualizacao(produtoId);
        if (dto.quantidade() < estoque.getQuantidadeReservada()) {
            throw new ConflitoDeEstadoException("O novo saldo (" + dto.quantidade()
                    + ") não pode ser menor que a quantidade reservada (" + estoque.getQuantidadeReservada() + ").");
        }
        int diferenca = dto.quantidade() - estoque.getQuantidade();
        estoqueRepository.definirSaldo(produtoId, dto.quantidade(), java.time.LocalDateTime.now());
        estoque = buscarEstoque(produtoId);
        if (dto.quantidadeMinima() != null) {
            estoque.setQuantidadeMinima(dto.quantidadeMinima());
        }
        if (dto.dataValidade() != null) {
            estoque.setDataValidade(dto.dataValidade());
        }
        Estoque salvo = estoqueRepository.save(estoque);
        String direcao = diferenca >= 0 ? "aumento" : "redução";
        movimentacaoEstoqueService.registrar(salvo.getProduto(), TipoMovimentacao.AJUSTE, Math.abs(diferenca), null,
                dto.observacao() != null ? dto.observacao() : "Ajuste de inventário: " + direcao);
        return EstoqueRespostaDTO.de(salvo);
    }

    private Estoque buscarEstoque(Long produtoId) {
        return estoqueRepository.buscarPorProduto(produtoId).orElseThrow(() -> new RecursoNaoEncontradoException(
                "Estoque não encontrado: o produto não existe ou é Sob Encomenda (sem controle físico)."));
    }

    private Estoque buscarEstoqueParaAtualizacao(Long produtoId) {
        return estoqueRepository.buscarPorProdutoParaAtualizacao(produtoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Estoque não encontrado: o produto não existe ou é Sob Encomenda (sem controle físico)."));
    }
}
