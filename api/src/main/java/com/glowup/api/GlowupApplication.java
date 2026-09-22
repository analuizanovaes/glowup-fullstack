package com.glowup.api;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import java.util.TimeZone;

@SpringBootApplication
public class GlowupApplication {

    public static void main(String[] args) {
        SpringApplication.run(GlowupApplication.class, args);
    }

    // Executa imediatamente após o Spring Boot inicializar
    @PostConstruct
    public void init() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
    }
}
