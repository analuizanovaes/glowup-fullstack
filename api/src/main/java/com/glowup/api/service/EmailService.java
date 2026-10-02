package com.glowup.api.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EmailService {

    public void enviarEmailTexto(String destinatario, String assunto, String mensagem) {
        // Mock: Simula o envio de e-mail no terminal durante o desenvolvimento local
        log.info("=====================================================");
        log.info("SIMULAÇÃO DE ENVIO DE E-MAIL (MOCK)");
        log.info("Para: {}", destinatario);
        log.info("Assunto: {}", assunto);
        log.info("Mensagem:\n{}", mensagem);
        log.info("=====================================================");
    }
}