package com.glowup.api.service;

import com.glowup.api.dto.TokenRespostaDTO;
import com.glowup.api.model.RefreshToken;
import com.glowup.api.model.Usuario;
import com.glowup.api.repository.RefreshTokenRepository;
import com.glowup.api.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Sessões renováveis: o access token (JWT) dura pouco e o refresh token (aleatório, 256 bits, guardado só como
 * hash) o renova sem pedir a senha. A cada renovação o refresh token é trocado (rotação); uma repetição nos
 * dez segundos de tolerância recebe o sucessor persistido, inclusive em outra instância. Reuso posterior revoga sessões.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private static final SecureRandom GERADOR_ALEATORIO = new SecureRandom();
    private static final long JANELA_REPETICAO_MS = 10_000L;
    private final Map<String, RenovacaoRecente> renovacoesRecentes = new ConcurrentHashMap<>();

    private record RenovacaoRecente(long expiraEm, TokenRespostaDTO resposta) {}

    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenService tokenService;
    private final UsuarioRepository usuarioRepository;

    @Value("${api.security.refresh.dias:7}")
    private long validadeEmDias = 7;

    @Value("${api.security.token.secret}")
    private String segredoDerivacaoRefresh;

    @Transactional
    public TokenRespostaDTO iniciarSessao(Usuario usuario) {
        return new TokenRespostaDTO(tokenService.gerarToken(usuario), emitirRefreshToken(usuario.getId()),
                usuario.isSenhaProvisoria());
    }

    /** A exceção de credencial inválida não faz rollback: o encerramento das sessões na reutilização precisa gravar. */
    @Transactional(noRollbackFor = BadCredentialsException.class)
    public synchronized TokenRespostaDTO renovar(String refreshToken) {
        String hash = calcularHash(refreshToken);
        RenovacaoRecente recente = renovacoesRecentes.get(hash);
        if (recente != null && recente.expiraEm() >= System.currentTimeMillis()) {
            return recente.resposta();
        }
        renovacoesRecentes.remove(hash);
        RefreshToken atual = refreshTokenRepository.buscarParaRotacao(hash)
                .orElseThrow(this::sessaoInvalida);

        if (atual.getExpiraEm().isBefore(LocalDateTime.now())) {
            throw sessaoInvalida();
        }
        if (atual.isRevogado()) {
            if (atual.getRotacionadoEm() != null
                    && !atual.getRotacionadoEm().plusNanos(JANELA_REPETICAO_MS * 1_000_000L).isBefore(LocalDateTime.now())
                    && atual.getTokenSubstitutoHash() != null) {
                Usuario usuario = usuarioRepository.findById(atual.getUsuarioId())
                        .filter(Usuario::isEnabled)
                        .orElseThrow(this::sessaoInvalida);
                String substituto = derivarTokenSubstituto(hash);
                if (calcularHash(substituto).equals(atual.getTokenSubstitutoHash())) {
                    return new TokenRespostaDTO(tokenService.gerarToken(usuario), substituto,
                            usuario.isSenhaProvisoria());
                }
            }
            refreshTokenRepository.revogarTodosDoUsuario(atual.getUsuarioId());
            log.warn("Refresh token reutilizado fora da janela de tolerância para o usuário {}.", atual.getUsuarioId());
            throw sessaoInvalida();
        }
        if (refreshTokenRepository.revogarSeAtivo(atual.getId()) == 0) {
            refreshTokenRepository.revogarTodosDoUsuario(atual.getUsuarioId());
            log.warn("Refresh token reutilizado para o usuário {}: todas as sessões foram encerradas.", atual.getUsuarioId());
            throw sessaoInvalida();
        }

        Usuario usuario = usuarioRepository.findById(atual.getUsuarioId())
                .filter(Usuario::isEnabled)
                .orElseThrow(this::sessaoInvalida);
        String substituto = derivarTokenSubstituto(hash);
        String hashSubstituto = calcularHash(substituto);
        refreshTokenRepository.save(RefreshToken.builder()
                .usuarioId(usuario.getId())
                .tokenHash(hashSubstituto)
                .expiraEm(LocalDateTime.now().plusDays(validadeEmDias))
                .build());
        // revogarSeAtivo usa bulk update e limpa o contexto JPA; manter o estado revogado no merge do objeto destacado.
        atual.setRevogado(true);
        atual.setRotacionadoEm(LocalDateTime.now());
        atual.setTokenSubstitutoHash(hashSubstituto);
        refreshTokenRepository.save(atual);
        TokenRespostaDTO resposta = new TokenRespostaDTO(tokenService.gerarToken(usuario), substituto,
                usuario.isSenhaProvisoria());
        renovacoesRecentes.put(hash, new RenovacaoRecente(System.currentTimeMillis() + JANELA_REPETICAO_MS, resposta));
        renovacoesRecentes.entrySet().removeIf(entry -> entry.getValue().expiraEm() < System.currentTimeMillis());
        return resposta;
    }

    /** Logout: revoga o refresh token informado. Responde igual quando o token não existe. */
    @Transactional
    public void encerrar(String refreshToken) {
        refreshTokenRepository.findByTokenHash(calcularHash(refreshToken))
                .ifPresent(token -> refreshTokenRepository.revogarSeAtivo(token.getId()));
    }

    /** Usado na troca/redefinição de senha e na exclusão da conta. */
    @Transactional
    public void revogarTodos(Long usuarioId) {
        refreshTokenRepository.revogarTodosDoUsuario(usuarioId);
    }

    @Scheduled(cron = "0 15 3 * * *", zone = "America/Sao_Paulo")
    @Transactional
    public void limparExpirados() {
        int removidos = refreshTokenRepository.apagarExpirados(LocalDateTime.now());
        log.info("Limpeza de refresh tokens: {} expirado(s) removido(s).", removidos);
    }

    private String emitirRefreshToken(Long usuarioId) {
        byte[] aleatorio = new byte[32];
        GERADOR_ALEATORIO.nextBytes(aleatorio);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(aleatorio);
        refreshTokenRepository.save(RefreshToken.builder()
                .usuarioId(usuarioId)
                .tokenHash(calcularHash(token))
                .expiraEm(LocalDateTime.now().plusDays(validadeEmDias))
                .build());
        return token;
    }

    /** Mesmo sucessor em qualquer instância, sem persistir o refresh token em texto puro. */
    private String derivarTokenSubstituto(String hashAnterior) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(segredoDerivacaoRefresh.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] assinatura = mac.doFinal(("glowup-refresh-rotation:" + hashAnterior).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(assinatura);
        } catch (java.security.GeneralSecurityException excecao) {
            throw new IllegalStateException("Não foi possível derivar o refresh token rotacionado.", excecao);
        }
    }

    private BadCredentialsException sessaoInvalida() {
        return new BadCredentialsException("Sessão inválida ou expirada. Faça login novamente.");
    }

    private static String calcularHash(String token) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException excecao) {
            throw new IllegalStateException(excecao);
        }
    }
}
