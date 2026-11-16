package com.glowup.api.dto;

import com.glowup.api.domain.MetodoPagamento;
import com.glowup.api.domain.Pagamento;
import com.glowup.api.domain.StatusPagamento;
import java.math.BigDecimal;

public record PagamentoAdminResumoDTO(MetodoPagamento metodo, StatusPagamento status, BigDecimal valor, Integer parcelas) {
    public static PagamentoAdminResumoDTO de(Pagamento p) {
        return new PagamentoAdminResumoDTO(p.getMetodo(), p.getStatus(), p.getValor(), p.getParcelas());
    }
}
