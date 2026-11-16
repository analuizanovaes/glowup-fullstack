package com.glowup.api.service;

import com.glowup.api.domain.ItemKit;
import com.glowup.api.domain.Kit;
import com.glowup.api.domain.TipoKit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EstoqueDisponibilidadeServiceTest {

    @Test
    void componenteInativoTornaKitIndisponivelSemFalhar() {
        Kit kit = new Kit();
        kit.setTipo(TipoKit.MANUAL);
        ItemKit componenteInativo = new ItemKit();
        componenteInativo.setQuantidade(1);
        componenteInativo.setProduto(null);
        kit.getItens().add(componenteInativo);

        assertEquals(0, new EstoqueDisponibilidadeService().calcular(kit));
    }
}
