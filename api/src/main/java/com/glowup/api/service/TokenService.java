package com.glowup.api.service;

import com.glowup.api.model.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Service
public class TokenService {

    @Value("${api.security.token.secret}")
    private String secret;

    /** Access token curto; a sessão continua pelo refresh token (POST /api/auth/refresh). */
    @Value("${api.security.token.expiracao-minutos:30}")
    private long expiracaoEmMinutos = 30;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String gerarToken(Usuario usuario) {
        return Jwts.builder()
                .subject(usuario.getEmail())
                .claim("id", usuario.getId())
                .claim("role", usuario.getRole().name())
                .claim("sv", versaoDaSenha(usuario))
                .issuer("GlowUp-API")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(expiracaoEmMinutos, ChronoUnit.MINUTES)))
                .signWith(getSigningKey())
                .compact();
    }

    public String validarToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .requireIssuer("GlowUp-API")
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
        } catch (Exception exception) {
            return "";
        }
    }

    /**
     * O token carrega uma "versão da senha" (impressão digital do hash). Quando a senha muda (troca ou
     * recuperação por OTP), todos os tokens antigos deixam de valer, inclusive o de quem roubou a sessão.
     */
    public boolean tokenCorrespondeASenhaAtual(String token, Usuario usuario) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .requireIssuer("GlowUp-API")
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return versaoDaSenha(usuario).equals(claims.get("sv", String.class));
        } catch (Exception exception) {
            return false;
        }
    }

    private String versaoDaSenha(Usuario usuario) {
        try {
            byte[] resumo = MessageDigest.getInstance("SHA-256")
                    .digest(usuario.getSenhaHash().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(resumo, 0, 8);
        } catch (NoSuchAlgorithmException excecao) {
            throw new IllegalStateException(excecao);
        }
    }
}
