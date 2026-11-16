package com.glowup.api.service;

import com.glowup.api.dto.UsuarioCadastroDTO;
import com.glowup.api.exception.EmailJaCadastradoException;
import com.glowup.api.model.Usuario;
import com.glowup.api.repository.TermoAceiteRepository;
import com.glowup.api.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @InjectMocks
    private UsuarioService usuarioService; 

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private TermoAceiteRepository termoAceiteRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("Deve lançar exceção e bloquear cadastro para usuário com 17 anos")
    void naoDeveCadastrarMenorDeIdade() {
        // 1. Prepara os dados (Arrange)
        UsuarioCadastroDTO dto = new UsuarioCadastroDTO(
                "Ana Jovem",
                "ana@teste.com",
                "Senha@123",
                LocalDate.now().minusYears(17),
                List.of("61999999999"), // Telefones primeiro
                true // Aceita Termos por último
        );

        // 2. Executa a ação e espera a falha (Act & Assert)
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            usuarioService.registrarUsuario(dto);
        });

        // 3. Verifica se a mensagem de erro é exatamente a da nossa regra de negócio
        assertEquals("O cadastro não é permitido para menores de 18 anos.", exception.getMessage());
    }

    @Test
    @DisplayName("Deve lançar exceção e bloquear cadastro para idade irreal (ex: 121 anos)")
    void naoDeveCadastrarIdadeIrreal() {
        UsuarioCadastroDTO dto2 = new UsuarioCadastroDTO(
                "Ana Velha",
                "ana@teste.com",
                "Senha@123",
                LocalDate.now().minusYears(121),
                List.of("61999999999"), // Telefones primeiro
                true // Aceita Termos por último
        );

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            usuarioService.registrarUsuario(dto2);
        });

        assertEquals("Data de nascimento inválida. Verifique o ano informado.", exception.getMessage());
    }

    @Test
    @DisplayName("Deve permitir cadastro de quem completa exatamente 18 anos hoje")
    void devePermitirCadastroNoDiaDoDecimoOitavoAniversario() {
        when(usuarioRepository.saveAndFlush(any(Usuario.class))).thenAnswer(chamada -> chamada.getArgument(0));
        UsuarioCadastroDTO dto = new UsuarioCadastroDTO(
                "Ana Maior", "Ana@Teste.com", "Senha@123",
                LocalDate.now().minusYears(18), List.of("(61) 99999-9999"), true);

        Usuario usuario = usuarioService.registrarUsuario(dto);

        assertEquals("ana@teste.com", usuario.getEmail());
        assertEquals("61999999999", usuario.getTelefones().get(0).getNumero());
        assertTrue(usuario.getTelefones().get(0).isPrincipal());
    }

    @Test
    @DisplayName("Deve bloquear quem completa 18 anos amanhã")
    void deveBloquearUmDiaAntesDoDecimoOitavoAniversario() {
        UsuarioCadastroDTO dto = new UsuarioCadastroDTO(
                "Ana Quase", "ana@teste.com", "Senha@123",
                LocalDate.now().minusYears(18).plusDays(1), List.of("61999999999"), true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> usuarioService.registrarUsuario(dto));

        assertEquals("O cadastro não é permitido para menores de 18 anos.", exception.getMessage());
    }

    @Test
    @DisplayName("Deve rejeitar e-mail já cadastrado, ignorando maiúsculas")
    void deveRejeitarEmailDuplicado() {
        when(usuarioRepository.existsByEmail("ana@teste.com")).thenReturn(true);
        UsuarioCadastroDTO dto = new UsuarioCadastroDTO(
                "Ana Repetida", "ANA@teste.com", "Senha@123",
                LocalDate.now().minusYears(30), List.of("61999999999"), true);

        assertThrows(EmailJaCadastradoException.class, () -> usuarioService.registrarUsuario(dto));
    }
}
