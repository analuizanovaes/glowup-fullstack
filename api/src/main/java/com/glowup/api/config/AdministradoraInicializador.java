package com.glowup.api.config;

import com.glowup.api.dto.RegrasSenha;
import com.glowup.api.model.Role;
import com.glowup.api.model.Usuario;
import com.glowup.api.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Cria a conta ADMIN (Domingas) na primeira subida, a partir de variáveis de ambiente.
 * Em produção pode não haver shell para promover um usuário por SQL. Nunca altera uma conta já existente.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdministradoraInicializador implements ApplicationRunner {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${glowup.admin.email:}")
    private String email;

    @Value("${glowup.admin.senha-inicial:}")
    private String senhaInicial;

    /** true (padrão) obriga a troca da senha no 1º acesso. Em ambiente local pode ser false (ADMIN_SENHA_PROVISORIA=false). */
    @Value("${glowup.admin.senha-provisoria:true}")
    private boolean senhaProvisoria;

    @Value("${glowup.admin.nome:Domingas}")
    private String nome;

    @Value("${glowup.admin.data-nascimento:1990-01-01}")
    private String dataNascimento;

    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank() || senhaInicial.isBlank()) {
            log.info("ADMIN_EMAIL/ADMIN_SENHA_INICIAL não definidos: nenhuma conta administradora será criada.");
            return;
        }
        String emailNormalizado = email.trim().toLowerCase(Locale.ROOT);
        if (usuarioRepository.existsByEmail(emailNormalizado)) {
            log.info("Conta {} já existe; nada a fazer.", emailNormalizado);
            return;
        }
        if (!Pattern.matches(RegrasSenha.REGEX_SENHA_FORTE, senhaInicial)) {
            throw new IllegalStateException("ADMIN_SENHA_INICIAL não atende à regra de senha forte. " + RegrasSenha.MENSAGEM_SENHA_FORTE);
        }
        RegrasSenha.validarLimiteBcrypt(senhaInicial);

        usuarioRepository.save(Usuario.builder()
                .nome(nome)
                .email(emailNormalizado)
                .senhaHash(passwordEncoder.encode(senhaInicial))
                .dataNascimento(LocalDate.parse(dataNascimento))
                .role(Role.ADMIN)
                .ativo(true)
                .senhaProvisoria(senhaProvisoria)
                .build());
        log.info("Conta administradora {} criada{}.", emailNormalizado,
                senhaProvisoria ? " com senha provisória: a API exigirá a troca no primeiro acesso" : " sem exigir troca de senha");
    }
}
