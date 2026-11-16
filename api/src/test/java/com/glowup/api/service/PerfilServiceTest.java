package com.glowup.api.service;

import com.glowup.api.domain.StatusPedido;
import com.glowup.api.exception.ConflitoDeEstadoException;
import com.glowup.api.model.Role;
import com.glowup.api.model.Telefone;
import com.glowup.api.model.Usuario;
import com.glowup.api.repository.EnderecoRepository;
import com.glowup.api.repository.PedidoRepository;
import com.glowup.api.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerfilServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private EnderecoRepository enderecoRepository;
    @Mock
    private TokenService tokenService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private PedidoRepository pedidoRepository;

    @InjectMocks
    private PerfilService servico;

    private Usuario cliente(Role role) {
        return Usuario.builder()
                .id(1L)
                .nome("Ana")
                .email("ana@teste.com")
                .senhaHash("hash")
                .dataNascimento(LocalDate.of(1990, 1, 1))
                .role(role)
                .build();
    }

    @Test
    @DisplayName("Excluir conta com senha errada é recusado e nada é alterado")
    void excluirContaExigeSenhaCorreta() {
        Usuario usuario = cliente(Role.CLIENT);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("errada", "hash")).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> servico.excluirConta(1L, "errada"));

        assertTrue(usuario.isAtivo());
        verify(usuarioRepository, never()).save(any());
        verify(refreshTokenService, never()).revogarTodos(any());
    }

    @Test
    @DisplayName("Excluir conta sem informar a senha é recusado")
    void excluirContaSemSenha() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(cliente(Role.CLIENT)));

        assertThrows(IllegalArgumentException.class, () -> servico.excluirConta(1L, null));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Com pedido em aberto a exclusão responde conflito e a conta continua ativa")
    void excluirContaBloqueadaComPedidoEmAberto() {
        Usuario usuario = cliente(Role.CLIENT);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("certa", "hash")).thenReturn(true);
        when(pedidoRepository.existsByClienteIdAndStatusIn(eq(1L), anyCollection())).thenReturn(true);

        assertThrows(ConflitoDeEstadoException.class, () -> servico.excluirConta(1L, "certa"));

        assertTrue(usuario.isAtivo());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Pedidos em aberto considerados: aguardando pagamento, pago, em separação e enviado")
    void statusEmAbertoConsiderados() {
        Usuario usuario = cliente(Role.CLIENT);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("certa", "hash")).thenReturn(true);
        when(pedidoRepository.existsByClienteIdAndStatusIn(eq(1L), anyCollection())).thenReturn(false);
        when(enderecoRepository.findByUsuarioIdOrderByPrincipalDescIdAsc(1L)).thenReturn(List.of());

        servico.excluirConta(1L, "certa");

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<Collection<StatusPedido>> captor = org.mockito.ArgumentCaptor.forClass(Collection.class);
        verify(pedidoRepository).existsByClienteIdAndStatusIn(eq(1L), captor.capture());
        assertTrue(captor.getValue().containsAll(List.of(StatusPedido.AGUARDANDO_PAGAMENTO, StatusPedido.PAGO,
                StatusPedido.EM_SEPARACAO, StatusPedido.ENVIADO)));
        assertFalse(captor.getValue().contains(StatusPedido.ENTREGUE));
        assertFalse(captor.getValue().contains(StatusPedido.CANCELADO));
    }

    @Test
    @DisplayName("Exclusão válida anonimiza a conta, desativa e revoga as sessões")
    void excluirContaValida() {
        Usuario usuario = cliente(Role.CLIENT);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("certa", "hash")).thenReturn(true);
        when(pedidoRepository.existsByClienteIdAndStatusIn(eq(1L), anyCollection())).thenReturn(false);
        when(enderecoRepository.findByUsuarioIdOrderByPrincipalDescIdAsc(1L)).thenReturn(List.of());

        servico.excluirConta(1L, "certa");

        assertFalse(usuario.isAtivo());
        assertEquals("Conta removida", usuario.getNome());
        assertEquals("conta-removida-1@inativo.glowup.invalid", usuario.getEmail());
        verify(usuarioRepository).save(usuario);
        verify(refreshTokenService).revogarTodos(1L);
    }

    @Test
    @DisplayName("A conta da administradora não pode ser excluída pela API")
    void adminNaoPodeSerExcluida() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(cliente(Role.ADMIN)));

        assertThrows(ConflitoDeEstadoException.class, () -> servico.excluirConta(1L, "qualquer"));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Não é possível passar de 5 telefones por conta")
    void limiteDeTelefones() {
        Usuario usuario = cliente(Role.CLIENT);
        List<Telefone> telefones = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            telefones.add(Telefone.builder().id((long) i + 1).numero("6199999000" + i).principal(i == 0).usuario(usuario).build());
        }
        usuario.setTelefones(telefones);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        assertThrows(IllegalArgumentException.class, () -> servico.adicionarTelefone(1L, "61988887777"));
        verify(usuarioRepository, never()).saveAndFlush(any());
    }
}
