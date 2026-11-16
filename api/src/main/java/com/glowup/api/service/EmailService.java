package com.glowup.api.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import jakarta.annotation.PostConstruct;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;

/**
 * Único ponto de saída de e-mails (OTP, recibo e alerta de venda).
 * Usa a API do Resend via HTTPS sem depender de portas SMTP.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final Environment ambiente;
    private final RestTemplate restTemplate = criarClienteHttp();

    @Value("${glowup.notificacoes.email-habilitado:false}")
    private boolean envioHabilitado;

    @Value("${resend.api-key:}")
    private String resendApiKey;

    // Com onboarding@resend.dev o Resend só entrega ao e-mail dono da conta. Em produção, use um domínio verificado.
    @Value("${resend.from:GlowUp <onboarding@resend.dev>}")
    private String remetente;

    private static RestTemplate criarClienteHttp() {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(5000);
        fabrica.setReadTimeout(10000);
        return new RestTemplate(fabrica);
    }

    /** Remetente de teste do Resend só entrega ao dono da conta: avisa no log quando for usado em produção. */
    @PostConstruct
    void avisarRemetenteDeTeste() {
        boolean local = ambiente.acceptsProfiles(Profiles.of("local", "test", "dev"));
        if (envioHabilitado && !local && remetente.contains("onboarding@resend.dev")) {
            log.warn("RESEND_FROM usa o remetente de teste (onboarding@resend.dev): e-mails só chegam ao dono da conta "
                    + "Resend. Verifique um domínio no Resend e defina RESEND_FROM (veja docs/CONFIGURACAO_AMBIENTE.md).");
        }
    }

    /** Assíncrono: a latência do provedor de e-mail não pode aparecer no tempo de resposta da API. */
    @Async
    public void enviarEmailTexto(String destinatario, String assunto, String mensagem) {
        if (!envioHabilitado) {
            registrarSemEnviar(destinatario, assunto, mensagem);
            return;
        }

        if (resendApiKey == null || resendApiKey.isBlank()) {
            log.warn("RESEND_API_KEY ausente: e-mail '{}' será apenas registrado (modo apresentação/fallback).", assunto);
            registrarSemEnviar(destinatario, assunto, mensagem);
            return;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(resendApiKey);

            Map<String, Object> body = Map.of(
                    "from", remetente,
                    "to", destinatario,
                    "subject", assunto,
                    "text", mensagem 
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            restTemplate.postForEntity("https://api.resend.com/emails", request, String.class);

            log.info("E-mail enviado com sucesso via Resend para: {}", destinatario);
        } catch (Exception excecao) {
            log.error("Falha no envio HTTPS do e-mail '{}': {}", assunto, excecao.getMessage());
        }
    }

    // O corpo (que pode conter um OTP) só é exibido fora de produção.
    private void registrarSemEnviar(String destinatario, String assunto, String mensagem) {
        if (!ambiente.acceptsProfiles(Profiles.of("local", "test", "dev"))) {
            log.warn("E-mail '{}' não enviado: EMAIL_ENVIO_HABILITADO=false.", assunto);
            return;
        }
        log.info("E-mail simulado (EMAIL_ENVIO_HABILITADO=false)\nPara: {}\nAssunto: {}\n{}", destinatario, assunto, mensagem);
    }
}