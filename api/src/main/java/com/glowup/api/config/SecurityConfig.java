package com.glowup.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final SecurityFilter securityFilter;
    private final ManutencaoFilter manutencaoFilter; 
    private final RespostasDeSegurancaJson respostasDeSeguranca;
    private final RateLimitFilter rateLimitFilter;
    private final List<String> origensPermitidas;

    public SecurityConfig(SecurityFilter securityFilter,
                          ManutencaoFilter manutencaoFilter, 
                          RespostasDeSegurancaJson respostasDeSeguranca,
                          RateLimitFilter rateLimitFilter,
                          @Value("${glowup.cors.allowed-origins:*}") List<String> origensPermitidas) {
        this.securityFilter = securityFilter;
        this.manutencaoFilter = manutencaoFilter;
        this.respostasDeSeguranca = respostasDeSeguranca;
        this.rateLimitFilter = rateLimitFilter;
        this.origensPermitidas = origensPermitidas;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(erros -> erros
                        .authenticationEntryPoint(respostasDeSeguranca)
                        .accessDeniedHandler(respostasDeSeguranca))
                .authorizeHttpRequests(req -> {
                    req.requestMatchers(HttpMethod.POST,
                            "/api/usuarios",
                            "/api/auth/login",
                            "/api/auth/refresh",
                            "/api/auth/logout",
                            "/api/auth/esqueci-senha",
                            "/api/auth/redefinir-senha").permitAll();

                    req.requestMatchers("/error").permitAll();
                    req.requestMatchers(HttpMethod.POST, "/api/webhooks/mercadopago").permitAll();
                    
                    // Endpoint de Health Check liberado para verificação de saúde da API
                    req.requestMatchers(HttpMethod.GET, "/api/health").permitAll();

                    // Versão vigente dos termos: o front precisa dela antes do cadastro/checkout
                    req.requestMatchers(HttpMethod.GET, "/api/termos/versao-atual").permitAll();

                    req.requestMatchers("/api/admin/**").hasRole("ADMIN");
                    req.requestMatchers(HttpMethod.GET, "/api/produtos/**", "/api/kits/**").authenticated();
                    req.requestMatchers("/api/produtos/**", "/api/kits/**").hasRole("ADMIN");

                    req.requestMatchers(
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/swagger-resources/**",
                        "/webjars/**"
                    ).permitAll();

                    req.anyRequest().authenticated();
                })

                // Ordem importa: o filtro de manutenção precisa enxergar quem está autenticada (administradora passa).
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(securityFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(manutencaoFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterServletRegistration(RateLimitFilter filter) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration)
            throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Custo 12 (padrão é 10): cada hash novo leva ~4x mais tempo para ser quebrado por força bruta.
        // Hashes antigos (custo 10) continuam válidos; só senhas novas/trocadas usam o custo 12.
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        if (origensPermitidas == null || origensPermitidas.isEmpty() || origensPermitidas.contains("*")) {
            // '*' é o padrão local; ValidadorConfiguracaoSeguranca não permite esse valor em produção.
            // O front estático pode ser servido por qualquer porta local (Live Server, Python etc.).
            configuration.setAllowedOriginPatterns(List.of("http://localhost:*", "http://127.0.0.1:*"));
        } else {
            configuration.setAllowedOrigins(origensPermitidas);
        }
        
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("X-Total-Count", "X-Total-Pages"));
        configuration.setAllowCredentials(true);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
