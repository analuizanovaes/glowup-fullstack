package com.glowup.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
@EnableAsync

public class GlowupApplication {

    public static void main(String[] args) {
        // Definido antes do contexto: o servidor pode rodar em UTC e as rotinas "à meia-noite" e prazos usam o horário de Brasília.
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
        SpringApplication.run(GlowupApplication.class, args);
    }
}
