package com.glowup.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.glowup.api.dto.UsuarioCadastroDTO;
import com.glowup.api.dto.UsuarioRespostaDTO;
import com.glowup.api.model.Telefone;
import com.glowup.api.model.Usuario;
import com.glowup.api.service.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List; 

@RestController
@Tag(name = "Usuários", description = "Cadastro de clientes (rota pública)")
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;

    @Operation(summary = "Cadastra uma nova cliente",
            description = "Exige 18 anos completos, aceite dos Termos de Uso, senha forte e ao menos um telefone. O CPF não é coletado.")
    @SecurityRequirements
    @PostMapping
    public ResponseEntity<UsuarioRespostaDTO> registrar(@RequestBody @Valid UsuarioCadastroDTO dto) {
        Usuario usuario = usuarioService.registrarUsuario(dto);
        
        List<String> telefonesResposta = usuario.getTelefones().stream()
                .map(Telefone::getNumero)
                .toList();

        UsuarioRespostaDTO resposta = new UsuarioRespostaDTO(
                usuario.getId(),
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getDataNascimento(),
                telefonesResposta
        );
        
        URI uri = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(usuario.getId())
                .toUri();

        return ResponseEntity.created(uri).body(resposta);
    }
}