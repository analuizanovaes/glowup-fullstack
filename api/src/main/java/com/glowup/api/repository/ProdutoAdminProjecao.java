package com.glowup.api.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public interface ProdutoAdminProjecao {
    Long getId();
    String getNome();
    String getDescricao();
    String getMarca();
    String getCategoria();
    String getTipoKit();
    String getModalidade();
    BigDecimal getCustoFabrica();
    BigDecimal getPrecoBase();
    BigDecimal getPrecoPromocional();
    LocalDateTime getDataLimitePromocao();
    String getImagemUrl();
    Boolean getAtivo();
    Integer getQuantidade();
    Integer getQuantidadeReservada();
    LocalDate getDataValidade();
    Integer getTempoMedioUsoDias();
}
