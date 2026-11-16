package com.glowup.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Collections;
import java.util.List;

public record SolicitacaoDevolucaoDTO(
    @NotBlank(message = "O motivo da devolução é obrigatório")
    @Size(max = 500, message = "O motivo deve ter no máximo 500 caracteres")
    String motivo,

    // Lista de itens específicos e quantidades que a cliente quer devolver
    @NotEmpty(message = "Informe ao menos um item para devolução")
    @Valid
    List<ItemDevolucaoRequisicaoDTO> itensDevolvidos,
    // Direito de arrependimento (CDC art. 49), declarado pela cliente
    Boolean arrependimento
) {

    public SolicitacaoDevolucaoDTO(String motivo, List<ItemDevolucaoRequisicaoDTO> itensDevolvidos) {
        this(motivo, itensDevolvidos, null);
    }

    
    public SolicitacaoDevolucaoDTO {
        if (itensDevolvidos == null) {
            itensDevolvidos = Collections.emptyList();
        }
    }
}