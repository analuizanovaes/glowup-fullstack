package com.glowup.api.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Impede que a API suba com configuração insegura (qualquer perfil que não seja local/test/dev): segredo JWT padrão (público no repositório,
 * permitiria forjar token ADMIN) ou integração de pagamento sem credencial / sem validação de assinatura do webhook.
 */
@Component
@Slf4j
public class ValidadorConfiguracaoSeguranca {

    private static final String SEGREDO_PADRAO_DE_DESENVOLVIMENTO =
            "chave_secreta_padrao_para_o_ambiente_de_desenvolvimento_local";
    private static final int TAMANHO_MINIMO_DO_SEGREDO = 32;

    private final Environment ambiente;
    private final String segredoJwt;
    private final String tokenMercadoPago;
    private final String segredoWebhook;
    private final String origensCors;
    private final boolean emailHabilitado;
    private final String resendApiKey;
    private final String resendFrom;
    private final boolean swaggerHabilitado;

    public ValidadorConfiguracaoSeguranca(Environment ambiente,
                                          @Value("${api.security.token.secret}") String segredoJwt,
                                          @Value("${mercadopago.access-token:}") String tokenMercadoPago,
                                          @Value("${mercadopago.webhook-secret:}") String segredoWebhook,
                                          @Value("${glowup.cors.allowed-origins:*}") String origensCors,
                                          @Value("${glowup.notificacoes.email-habilitado:false}") boolean emailHabilitado,
                                          @Value("${resend.api-key:}") String resendApiKey,
                                          @Value("${resend.from:}") String resendFrom,
                                          @Value("${springdoc.swagger-ui.enabled:false}") boolean swaggerHabilitado) {
        this.ambiente = ambiente;
        this.segredoJwt = segredoJwt;
        this.tokenMercadoPago = tokenMercadoPago;
        this.segredoWebhook = segredoWebhook;
        this.origensCors = origensCors;
        this.emailHabilitado = emailHabilitado;
        this.resendApiKey = resendApiKey;
        this.resendFrom = resendFrom;
        this.swaggerHabilitado = swaggerHabilitado;
    }

    @PostConstruct
    void validar() {
        // Seguro por padrão: só é tolerante quando o perfil é EXPLICITAMENTE local/test/dev.
        // Esquecer SPRING_PROFILES_ACTIVE em produção passa a barrar a subida, em vez de liberar o segredo público.
        boolean ambienteLocal = ambiente.acceptsProfiles(Profiles.of("local", "test", "dev"));
        boolean producao = !ambienteLocal;
        List<String> problemas = new ArrayList<>();

        if (segredoJwtInseguro()) {
            problemas.add("JWT_SECRET ausente, com menos de 32 caracteres ou igual ao padrão de desenvolvimento");
        }
        if (tokenMercadoPago.isBlank()) {
            problemas.add("MP_TEST_ACCESS_TOKEN ausente (este build só aceita credencial de teste)");
        }
        if (segredoWebhook.isBlank()) {
            problemas.add("MP_WEBHOOK_SECRET ausente (webhooks sem validação de assinatura)");
        }

        if (origensCors == null || origensCors.isBlank() || origensCors.contains("*")) {
            problemas.add("CORS_ALLOWED_ORIGINS ausente ou com '*' (informe a URL do front-end)");
        }
        if (swaggerHabilitado) {
            problemas.add("SWAGGER_ENABLED=true (a documentação da API não pode ficar pública em produção)");
        }
        if (emailHabilitado) {
            if (resendApiKey == null || resendApiKey.isBlank()) {
                problemas.add("EMAIL_ENVIO_HABILITADO=true, mas RESEND_API_KEY está ausente");
            }
            if (resendFrom == null || resendFrom.isBlank() || resendFrom.contains("onboarding@resend.dev")) {
                problemas.add("EMAIL_ENVIO_HABILITADO=true exige RESEND_FROM de um domínio verificado no Resend");
            }
        }

        if (problemas.isEmpty()) {
            return;
        }
        if (producao) {
            throw new IllegalStateException("Configuração insegura: " + String.join("; ", problemas)
                    + ". Defina as variáveis de ambiente e SPRING_PROFILES_ACTIVE=prod "
                    + "(localmente, use SPRING_PROFILES_ACTIVE=local).");
        }
        log.warn("Configuração de desenvolvimento com pendências (inaceitáveis em produção): {}", problemas);
    }

    private boolean segredoJwtInseguro() {
        return SEGREDO_PADRAO_DE_DESENVOLVIMENTO.equals(segredoJwt) || segredoJwt.length() < TAMANHO_MINIMO_DO_SEGREDO;
    }
}
