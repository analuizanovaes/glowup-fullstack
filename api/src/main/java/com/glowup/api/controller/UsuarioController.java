package com.glowup.api.controller;

import com.glowup.api.dto.UsuarioCadastroDTO;
import com.glowup.api.dto.UsuarioRespostaDTO;
import com.glowup.api.model.Usuario;
import com.glowup.api.service.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;

    @PostMapping
    public ResponseEntity<UsuarioRespostaDTO> registrar(@RequestBody @Valid UsuarioCadastroDTO dto) {
        Usuario usuario = usuarioService.registrarUsuario(dto);
        
        UsuarioRespostaDTO resposta = new UsuarioRespostaDTO(
                usuario.getId(),
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getDataNascimento()
        );

        // Constrói automaticamente o cabeçalho "Location" com a URL completa do novo recurso 
        URI uri = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(usuario.getId())
                .toUri();

        return ResponseEntity.created(uri).body(resposta);
    }
}