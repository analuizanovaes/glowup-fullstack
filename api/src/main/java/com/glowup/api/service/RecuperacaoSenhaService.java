package com.glowup.api.service;

import com.glowup.api.model.RecuperacaoSenha;
import com.glowup.api.model.Usuario;
import com.glowup.api.dto.RedefinicaoSenhaDTO;
import com.glowup.api.repository.RecuperacaoSenhaRepository;
import com.glowup.api.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class RecuperacaoSenhaService {

    private final UsuarioRepository usuarioRepository;
    private final RecuperacaoSenhaRepository recuperacaoRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void solicitarCodigo(String email) {
        var usuarioOpt = usuarioRepository.findByEmail(email);
        
        // Bloqueio de Enumeração: Encerra o fluxo silenciosamente se o e-mail não existir
        if (usuarioOpt.isEmpty()) {
            return;
        }
        
        Usuario usuario = usuarioOpt.get();
        String codigo = String.format("%06d", new Random().nextInt(1000000));

        RecuperacaoSenha recuperacao = RecuperacaoSenha.builder()
                .codigoOtp(codigo)
                .expiracao(LocalDateTime.now().plusMinutes(10)) // Expira rigidamente em 10 minutos
                .usuario(usuario)
                .build();

        recuperacaoRepository.save(recuperacao);

        String mensagem = String.format(
                "Olá %s,\n\nSeu código de recuperação de senha é: %s\nEle expira em 10 minutos.",
                usuario.getNome(), codigo
        );

        emailService.enviarEmailTexto(usuario.getEmail(), "Recuperação de Senha - GlowUp", mensagem);
    }

    @Transactional
    public void redefinirSenha(RedefinicaoSenhaDTO dto) {
        RecuperacaoSenha recuperacao = recuperacaoRepository
                .findByUsuarioEmailAndCodigoOtpAndUtilizadoFalse(dto.email(), dto.codigoOtp())
                .orElseThrow(() -> new IllegalArgumentException("Código inválido ou já utilizado."));

        if (!recuperacao.isValido()) {
            throw new IllegalArgumentException("O código OTP expirou. Por favor, solicite um novo.");
        }

        Usuario usuario = recuperacao.getUsuario();
        usuario.setSenhaHash(passwordEncoder.encode(dto.novaSenha())); // Sobrescreve com o novo hash irreversível
        recuperacao.setUtilizado(true); // Invalida o código para impedir reuso

        usuarioRepository.save(usuario);
        recuperacaoRepository.save(recuperacao);
    }
}