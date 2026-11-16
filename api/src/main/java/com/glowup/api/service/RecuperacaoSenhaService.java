package com.glowup.api.service;

import com.glowup.api.dto.RedefinicaoSenhaDTO;
import com.glowup.api.exception.CodigoOtpInvalidoException;
import com.glowup.api.model.RecuperacaoSenha;
import com.glowup.api.model.Usuario;
import com.glowup.api.repository.RecuperacaoSenhaRepository;
import com.glowup.api.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class RecuperacaoSenhaService {

    private static final int VALIDADE_EM_MINUTOS = 10;
    private static final int MAXIMO_DE_TENTATIVAS = 5;
    private static final int LIMITE_DO_CODIGO = 1_000_000;
    /** Intervalo mínimo entre dois e-mails de código para a mesma conta (evita encher a caixa de terceiros). */
    private static final int INTERVALO_MINIMO_ENTRE_CODIGOS_SEGUNDOS = 60;
    private static final SecureRandom GERADOR_ALEATORIO = new SecureRandom();

    private final UsuarioRepository usuarioRepository;
    private final RecuperacaoSenhaRepository recuperacaoRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    /** Responde sempre igual, exista ou não o e-mail, para não permitir enumeração de contas. */
    @Transactional
    public void solicitarCodigo(String email) {
        usuarioRepository.findByEmail(normalizarEmail(email)).ifPresentOrElse(this::emitirCodigo, this::simularCustoDeEmissao);
    }

    /**
     * As tentativas erradas precisam ser gravadas mesmo quando a requisição falha,
     * por isso a exceção de código inválido não provoca rollback.
     */
    @Transactional(noRollbackFor = CodigoOtpInvalidoException.class)
    public void redefinirSenha(RedefinicaoSenhaDTO dto) {
        com.glowup.api.dto.RegrasSenha.validarLimiteBcrypt(dto.novaSenha());
        Usuario usuario = usuarioRepository.findByEmail(normalizarEmail(dto.email()))
                .orElseThrow(CodigoOtpInvalidoException::new);
        RecuperacaoSenha recuperacao = recuperacaoRepository
                .findFirstByUsuarioIdAndUtilizadoFalseOrderByCriadoEmDesc(usuario.getId())
                .orElseThrow(CodigoOtpInvalidoException::new);

        validarCodigo(recuperacao, dto.codigoOtp());

        usuario.setSenhaHash(passwordEncoder.encode(dto.novaSenha()));
        usuario.setSenhaProvisoria(false);
        refreshTokenService.revogarTodos(usuario.getId());
        recuperacao.setUtilizado(true);
        recuperacao.setUtilizadoEm(LocalDateTime.now());
    }

    private void emitirCodigo(Usuario usuario) {
        if (emitidoRecentemente(usuario)) {
            // Mesma resposta e mesmo custo de processamento: quem consulta não distingue este caso.
            simularCustoDeEmissao();
            return;
        }
        recuperacaoRepository.invalidarCodigosAtivos(usuario.getId(), LocalDateTime.now());

        String codigo = gerarCodigoNumerico();
        recuperacaoRepository.save(RecuperacaoSenha.builder()
                .codigoOtpHash(passwordEncoder.encode(codigo))
                .expiracao(LocalDateTime.now().plusMinutes(VALIDADE_EM_MINUTOS))
                .usuario(usuario)
                .build());

        emailService.enviarEmailTexto(usuario.getEmail(), "Recuperação de Senha - GlowUp", montarMensagem(usuario, codigo));
    }

    private boolean emitidoRecentemente(Usuario usuario) {
        LocalDateTime limite = LocalDateTime.now().minusSeconds(INTERVALO_MINIMO_ENTRE_CODIGOS_SEGUNDOS);
        return recuperacaoRepository.findFirstByUsuarioIdAndUtilizadoFalseOrderByCriadoEmDesc(usuario.getId())
                .map(RecuperacaoSenha::getCriadoEm)
                .filter(criadoEm -> criadoEm != null && criadoEm.isAfter(limite))
                .isPresent();
    }

    /** Gasta o mesmo hash BCrypt da emissão real, para o tempo de resposta não revelar se o e-mail existe. */
    private void simularCustoDeEmissao() {
        passwordEncoder.encode(gerarCodigoNumerico());
    }

    private void validarCodigo(RecuperacaoSenha recuperacao, String codigoInformado) {
        if (!recuperacao.isValido() || recuperacao.getTentativas() >= MAXIMO_DE_TENTATIVAS) {
            throw new CodigoOtpInvalidoException();
        }
        if (passwordEncoder.matches(codigoInformado, recuperacao.getCodigoOtpHash())) {
            return;
        }
        registrarTentativaErrada(recuperacao);
        throw new CodigoOtpInvalidoException();
    }

    private void registrarTentativaErrada(RecuperacaoSenha recuperacao) {
        recuperacao.setTentativas(recuperacao.getTentativas() + 1);
        if (recuperacao.getTentativas() >= MAXIMO_DE_TENTATIVAS) {
            recuperacao.setUtilizado(true);
            recuperacao.setUtilizadoEm(LocalDateTime.now());
        }
    }

    private String gerarCodigoNumerico() {
        return String.format("%06d", GERADOR_ALEATORIO.nextInt(LIMITE_DO_CODIGO));
    }

    private String montarMensagem(Usuario usuario, String codigo) {
        return String.format("Olá %s,%n%nSeu código de recuperação de senha é: %s%nEle expira em %d minutos.",
                usuario.getNome(), codigo, VALIDADE_EM_MINUTOS);
    }

    private String normalizarEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
