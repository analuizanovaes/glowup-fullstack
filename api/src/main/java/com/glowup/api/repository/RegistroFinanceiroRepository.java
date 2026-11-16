package com.glowup.api.repository;

import com.glowup.api.domain.RegistroFinanceiro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RegistroFinanceiroRepository extends JpaRepository<RegistroFinanceiro, Long> {

    @Query("SELECT r FROM RegistroFinanceiro r WHERE r.pedido.id IN :pedidoIds")
    List<RegistroFinanceiro> buscarPorPedidos(@Param("pedidoIds") List<Long> pedidoIds);

    Optional<RegistroFinanceiro> findByPedidoId(Long pedidoId);

    @Query("SELECT COUNT(r) FROM RegistroFinanceiro r "
            + "WHERE r.estornado = false AND r.criadoEm >= :inicio AND r.criadoEm < :fim")
    long contarVendasNoPeriodo(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    @Query("SELECT SUM(r.valorVenda) FROM RegistroFinanceiro r "
            + "WHERE r.estornado = false AND r.criadoEm >= :inicio AND r.criadoEm < :fim")
    BigDecimal somarFaturamentoNoPeriodo(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    @Query("SELECT SUM(r.custoFabrica) FROM RegistroFinanceiro r WHERE r.estornado = false "
            + "AND r.dadosSuficientes = true AND r.criadoEm >= :inicio AND r.criadoEm < :fim")
    BigDecimal somarCustosNoPeriodo(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    @Query("SELECT SUM(r.taxaGateway) FROM RegistroFinanceiro r WHERE r.estornado = false "
            + "AND r.dadosSuficientes = true AND r.criadoEm >= :inicio AND r.criadoEm < :fim")
    BigDecimal somarTaxasNoPeriodo(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    @Query("SELECT SUM(r.lucroReal) FROM RegistroFinanceiro r WHERE r.estornado = false "
            + "AND r.dadosSuficientes = true AND r.criadoEm >= :inicio AND r.criadoEm < :fim")
    BigDecimal somarLucroNoPeriodo(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    @Query("SELECT COUNT(r) FROM RegistroFinanceiro r WHERE r.estornado = false "
            + "AND r.dadosSuficientes = false AND r.criadoEm >= :inicio AND r.criadoEm < :fim")
    long contarComDadosInsuficientesNoPeriodo(@Param("inicio") LocalDateTime inicio,
                                              @Param("fim") LocalDateTime fim);
}
