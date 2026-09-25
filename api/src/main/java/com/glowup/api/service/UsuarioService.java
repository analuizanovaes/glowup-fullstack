package com.glowup.api.service;

import com.glowup.api.model.Role;
import com.glowup.api.model.TermoAceite;
import com.glowup.api.model.Usuario;
import com.glowup.api.dto.UsuarioCadastroDTO;
import com.glowup.api.repository.TermoAceiteRepository;
import com.glowup.api.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final TermoAceiteRepository termoAceiteRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Usuario registrarUsuario(UsuarioCadastroDTO dto) {
        
        if (usuarioRepository.existsByEmail(dto.email())) {
            throw new IllegalArgumentException("O e-mail informado já está registado na plataforma.");
        }

        int idade = Period.between(dto.dataNascimento(), LocalDate.now()).getYears();
        if (idade < 18) {
            throw new IllegalArgumentException("O registo não é permitido para menores de 18 anos.");
        }

        Usuario novoUsuario = Usuario.builder()
                .nome(dto.nome())
                .email(dto.email())
                .senhaHash(passwordEncoder.encode(dto.senha()))
                .dataNascimento(dto.dataNascimento())
                .role(Role.CLIENT)
                .ativo(true)
                .build();

        Usuario usuarioSalvo = usuarioRepository.save(novoUsuario);

        // Termo salvo diretamente, pois o @AssertTrue garantiu a validação no Controller
        TermoAceite termo = TermoAceite.builder()
                .dataHoraAceite(LocalDateTime.now())
                .versaoDocumento("v1.0")
                .usuario(usuarioSalvo)
                .build();
        termoAceiteRepository.save(termo);

        return usuarioSalvo;
    }
}