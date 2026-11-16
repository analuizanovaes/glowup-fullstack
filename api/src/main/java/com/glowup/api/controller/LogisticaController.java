package com.glowup.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.glowup.api.dto.ConsultaCepRespostaDTO;
import com.glowup.api.service.LogisticaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@Tag(name = "Logística", description = "Cálculo de frete (RN03)")
@RequestMapping("/api/logistica")
@RequiredArgsConstructor
public class LogisticaController {
    
    private final LogisticaService logisticaService;

    @Operation(summary = "Calcula o frete domiciliar fixo",
            description = "R$ 10,00 apenas para Itapoã e Paranoá (DF). Parâmetros: pais (caminho, ex.: BR), uf (ex.: DF) e bairro (ex.: Itapoã).")
    @GetMapping("/frete/{pais}")
    public ResponseEntity<BigDecimal> consultarFrete(
            @PathVariable String pais,
            @RequestParam String uf,
            @RequestParam String bairro) {
        BigDecimal valorFrete = logisticaService.calcularFrete(pais, uf, bairro);
        return ResponseEntity.ok(valorFrete);
    }

    @Operation(summary = "Consulta o CEP e as opções de entrega",
            description = "Usa o ViaCEP. Se o serviço falhar (HTTP 503) ou o CEP vier sem rua, "
                    + "permiteDigitacaoManual = true: libere os campos de endereço para digitação.")
    @GetMapping("/cep/{cep}")
    public ResponseEntity<ConsultaCepRespostaDTO> consultarCep(@PathVariable String cep) {
        ConsultaCepRespostaDTO resposta = logisticaService.consultarCep(cep);
        if (resposta.servicoIndisponivel()) {
            return ResponseEntity.status(503).body(resposta);
        }
        return ResponseEntity.ok(resposta);
    }
}