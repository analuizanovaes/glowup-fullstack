package com.glowup.api.controller;

import com.glowup.api.dto.EnderecoRequisicaoDTO;
import com.glowup.api.dto.ExclusaoContaDTO;
import com.glowup.api.dto.EnderecoRespostaDTO;
import com.glowup.api.dto.PerfilAtualizacaoDTO;
import com.glowup.api.dto.PerfilRespostaDTO;
import com.glowup.api.dto.TelefoneRequisicaoDTO;
import com.glowup.api.dto.TokenRespostaDTO;
import com.glowup.api.dto.TrocaSenhaDTO;
import com.glowup.api.dto.TelefoneRespostaDTO;
import com.glowup.api.model.Usuario;
import com.glowup.api.service.PerfilService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Tag(name = "Meu Perfil", description = "Dados, telefones e endereços da cliente autenticada (RF01)")
@RequestMapping("/api/usuarios/me")
@RequiredArgsConstructor
public class PerfilController {

    private final PerfilService perfilService;

    @Operation(summary = "Consulta o próprio perfil")
    @GetMapping
    public ResponseEntity<PerfilRespostaDTO> consultar(@AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(perfilService.consultar(usuario.getId()));
    }

    @Operation(summary = "Atualiza nome e e-mail",
            description = "Se o e-mail mudar, a resposta traz novoToken: use-o nas próximas chamadas. O histórico de pedidos é mantido.")
    @PutMapping
    public ResponseEntity<PerfilRespostaDTO> atualizar(
            @AuthenticationPrincipal Usuario usuario, @RequestBody @Valid PerfilAtualizacaoDTO dto) {
        return ResponseEntity.ok(perfilService.atualizar(usuario.getId(), dto));
    }

    @Operation(summary = "Troca a própria senha",
            description = "Exige a senha atual. Devolve token e refreshToken novos: as sessões anteriores deixam de valer. Também libera a conta com senha provisória.")
    @PutMapping("/senha")
    public ResponseEntity<TokenRespostaDTO> trocarSenha(
            @AuthenticationPrincipal Usuario usuario, @RequestBody @Valid TrocaSenhaDTO dto) {
        return ResponseEntity.ok(perfilService.trocarSenha(usuario.getId(), dto));
    }

    @Operation(summary = "Exclui a própria conta (exclusão lógica)",
            description = "Exige a senha atual no corpo ({\"senha\": \"...\"}). Com pedido em aberto (aguardando pagamento, pago, "
                    + "em separação ou enviado) a resposta é 409.")
    @DeleteMapping
    public ResponseEntity<Void> excluirConta(
            @AuthenticationPrincipal Usuario usuario, @RequestBody @Valid ExclusaoContaDTO dto) {
        perfilService.excluirConta(usuario.getId(), dto.senha());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Adiciona um telefone")
    @PostMapping("/telefones")
    public ResponseEntity<List<TelefoneRespostaDTO>> adicionarTelefone(
            @AuthenticationPrincipal Usuario usuario, @RequestBody @Valid TelefoneRequisicaoDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(perfilService.adicionarTelefone(usuario.getId(), dto.numero()));
    }

    @Operation(summary = "Remove um telefone (é obrigatório manter ao menos um)")
    @DeleteMapping("/telefones/{telefoneId}")
    public ResponseEntity<List<TelefoneRespostaDTO>> removerTelefone(
            @AuthenticationPrincipal Usuario usuario, @PathVariable Long telefoneId) {
        return ResponseEntity.ok(perfilService.removerTelefone(usuario.getId(), telefoneId));
    }

    @Operation(summary = "Define o telefone principal")
    @PatchMapping("/telefones/{telefoneId}/principal")
    public ResponseEntity<List<TelefoneRespostaDTO>> telefonePrincipal(
            @AuthenticationPrincipal Usuario usuario, @PathVariable Long telefoneId) {
        return ResponseEntity.ok(perfilService.definirTelefonePrincipal(usuario.getId(), telefoneId));
    }

    @Operation(summary = "Lista os endereços")
    @GetMapping("/enderecos")
    public ResponseEntity<List<EnderecoRespostaDTO>> listarEnderecos(@AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(perfilService.listarEnderecos(usuario.getId()));
    }

    @Operation(summary = "Cadastra um endereço (o primeiro vira o principal)")
    @PostMapping("/enderecos")
    public ResponseEntity<EnderecoRespostaDTO> criarEndereco(
            @AuthenticationPrincipal Usuario usuario,
            @RequestBody @Valid EnderecoRequisicaoDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(perfilService.criarEndereco(usuario.getId(), dto));
    }

    @Operation(summary = "Edita um endereço")
    @PutMapping("/enderecos/{enderecoId}")
    public ResponseEntity<EnderecoRespostaDTO> atualizarEndereco(
            @AuthenticationPrincipal Usuario usuario, @PathVariable Long enderecoId,
            @RequestBody @Valid EnderecoRequisicaoDTO dto) {
        return ResponseEntity.ok(perfilService.atualizarEndereco(usuario.getId(), enderecoId, dto));
    }

    @Operation(summary = "Define o endereço principal")
    @PatchMapping("/enderecos/{enderecoId}/principal")
    public ResponseEntity<EnderecoRespostaDTO> enderecoPrincipal(
            @AuthenticationPrincipal Usuario usuario, @PathVariable Long enderecoId) {
        return ResponseEntity.ok(perfilService.definirEnderecoPrincipal(usuario.getId(), enderecoId));
    }

    @Operation(summary = "Exclui (logicamente) um endereço")
    @DeleteMapping("/enderecos/{enderecoId}")
    public ResponseEntity<Void> excluirEndereco(
            @AuthenticationPrincipal Usuario usuario, @PathVariable Long enderecoId) {
        perfilService.excluirEndereco(usuario.getId(), enderecoId);
        return ResponseEntity.noContent().build();
    }
}
