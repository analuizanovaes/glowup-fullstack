package com.glowup.api.service;

import com.glowup.api.dto.UsuarioCadastroDTO;
import com.glowup.api.exception.EmailJaCadastradoException;
import com.glowup.api.model.Role;
import com.glowup.api.model.Telefone;
import com.glowup.api.model.TermoAceite;
import com.glowup.api.model.Usuario;
import com.glowup.api.repository.TermoAceiteRepository;
import com.glowup.api.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private static final int IDADE_MINIMA = 18;
    private static final int IDADE_MAXIMA = 120;
    private static final String TIPO_ACEITE_TERMOS_DE_USO = "TERMOS_DE_USO";

    /** Versão vigente dos termos (glowup.termos.versao-atual): a mesma que o front lê em /api/termos/versao-atual. */
    @org.springframework.beans.factory.annotation.Value("${glowup.termos.versao-atual:1.0}")
    private String versaoTermosAtual = "1.0";

    private final UsuarioRepository usuarioRepository;
    private final TermoAceiteRepository termoAceiteRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Usuario registrarUsuario(UsuarioCadastroDTO dto) {
        com.glowup.api.dto.RegrasSenha.validarLimiteBcrypt(dto.senha());
        validarIdade(dto.dataNascimento());
        validarVersaoDosTermos(dto.versaoTermos());

        String email = normalizarEmail(dto.email());
        if (usuarioRepository.existsByEmail(email)) {
            throw new EmailJaCadastradoException();
        }

        Usuario usuarioSalvo = salvarUsuario(montarUsuario(dto, email));
        registrarAceiteDosTermos(usuarioSalvo);
        return usuarioSalvo;
    }

    private void validarIdade(LocalDate dataNascimento) {
        int idade = Period.between(dataNascimento, LocalDate.now()).getYears();
        if (idade < IDADE_MINIMA) {
            throw new IllegalArgumentException("O cadastro não é permitido para menores de 18 anos.");
        }
        if (idade > IDADE_MAXIMA) {
            throw new IllegalArgumentException("Data de nascimento inválida. Verifique o ano informado.");
        }
    }

    // Se o front informou a versão exibida, ela precisa ser a vigente (o aceite é de um documento específico).
    private void validarVersaoDosTermos(String versaoInformada) {
        if (versaoInformada != null && !versaoInformada.isBlank() && !versaoTermosAtual.equals(versaoInformada.trim())) {
            throw new IllegalArgumentException(
                    "Os Termos de Uso foram atualizados. Recarregue a página, leia a versão vigente e aceite novamente.");
        }
    }

    private Usuario montarUsuario(UsuarioCadastroDTO dto, String email) {
        Usuario usuario = Usuario.builder()
                .nome(dto.nome().trim())
                .email(email)
                .senhaHash(passwordEncoder.encode(dto.senha()))
                .dataNascimento(dto.dataNascimento())
                .role(Role.CLIENT)
                .ativo(true)
                .build();
        usuario.getTelefones().addAll(montarTelefones(dto.telefones(), usuario));
        return usuario;
    }

    // O primeiro telefone informado é o principal; números repetidos são descartados.
    private List<Telefone> montarTelefones(List<String> numerosInformados, Usuario usuario) {
        Set<String> numeros = new LinkedHashSet<>();
        numerosInformados.forEach(numero -> numeros.add(somenteDigitos(numero)));

        List<Telefone> telefones = new ArrayList<>();
        for (String numero : numeros) {
            telefones.add(Telefone.builder()
                    .numero(numero)
                    .principal(telefones.isEmpty())
                    .usuario(usuario)
                    .build());
        }
        return telefones;
    }

    private String somenteDigitos(String numero) {
        String digitos = numero.replaceAll("\\D", "");
        if (digitos.length() < 10 || digitos.length() > 13) {
            throw new IllegalArgumentException("Telefone inválido. Informe DDD + número.");
        }
        return digitos;
    }

    // Cobre a corrida entre duas requisições simultâneas com o mesmo e-mail (a unicidade final é do banco).
    private Usuario salvarUsuario(Usuario usuario) {
        try {
            return usuarioRepository.saveAndFlush(usuario);
        } catch (DataIntegrityViolationException excecao) {
            throw new EmailJaCadastradoException();
        }
    }

    // RD04: data, hora exata e versão do documento aceito, vinculadas ao ID do usuário.
    private void registrarAceiteDosTermos(Usuario usuario) {
        termoAceiteRepository.save(TermoAceite.builder()
                .tipoAceite(TIPO_ACEITE_TERMOS_DE_USO)
                .versaoDocumento(versaoTermosAtual)
                .dataHoraAceite(LocalDateTime.now())
                .usuario(usuario)
                .build());
    }

    private String normalizarEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
