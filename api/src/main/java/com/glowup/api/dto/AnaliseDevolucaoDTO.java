package com.glowup.api.dto;

import jakarta.validation.constraints.Size;
import java.util.List;

public record AnaliseDevolucaoDTO(
    Boolean produtoLacrado,

    @Size(max = 500, message = "A observação deve ter no máximo 500 caracteres")
    String observacao,
    List<ItemLacreDTO> itens
) {
    public AnaliseDevolucaoDTO(Boolean produtoLacrado, String observacao) {
        this(produtoLacrado, observacao, null);
    }
}
