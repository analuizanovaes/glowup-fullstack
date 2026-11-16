package com.glowup.api.service;

import com.glowup.api.domain.StatusPedido;
import com.glowup.api.dto.EnderecoRequisicaoDTO;
import com.glowup.api.dto.EnderecoRespostaDTO;
import com.glowup.api.dto.PerfilAtualizacaoDTO;
import com.glowup.api.dto.PerfilRespostaDTO;
import com.glowup.api.dto.TokenRespostaDTO;
import com.glowup.api.dto.TrocaSenhaDTO;
import com.glowup.api.dto.TelefoneRespostaDTO;
import com.glowup.api.exception.ConflitoDeEstadoException;
import com.glowup.api.exception.EmailJaCadastradoException;
import com.glowup.api.exception.RecursoNaoEncontradoException;
import com.glowup.api.model.Endereco;
import com.glowup.api.model.Role;
import com.glowup.api.model.Telefone;
import com.glowup.api.model.Usuario;
import com.glowup.api.repository.EnderecoRepository;
import com.glowup.api.repository.PedidoRepository;
import com.glowup.api.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * "Meu Perfil" (RF01): dados, telefones e endereços da cliente autenticada. Tudo é buscado pelo ID do
 * token, então ninguém acessa dados de outra pessoa. A troca de e-mail não afeta pedidos (vínculo por ID).
 */
@Service
@RequiredArgsConstructor
public class PerfilService {

    private final UsuarioRepository usuarioRepository;
    private final EnderecoRepository enderecoRepository;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final PedidoRepository pedidoRepository;

    private static final int MAXIMO_TELEFONES = 5;
    private static final List<StatusPedido> STATUS_PEDIDO_EM_ABERTO = List.of(
            StatusPedido.AGUARDANDO_PAGAMENTO, StatusPedido.PAGO, StatusPedido.EM_SEPARACAO, StatusPedido.ENVIADO);

    @Transactional(readOnly = true)
    public PerfilRespostaDTO consultar(Long usuarioId) {
        return montarPerfil(buscarUsuario(usuarioId), null);
    }

    @Transactional
    public PerfilRespostaDTO atualizar(Long usuarioId, PerfilAtualizacaoDTO dto) {
        Usuario usuario = buscarUsuario(usuarioId);
        String novoEmail = dto.email().trim().toLowerCase(Locale.ROOT);
        boolean emailMudou = !novoEmail.equals(usuario.getEmail());

        if (emailMudou && (dto.senhaAtual() == null
                || !passwordEncoder.matches(dto.senhaAtual(), usuario.getSenhaHash()))) {
            throw new IllegalArgumentException("Informe a senha atual correta para alterar o e-mail.");
        }
        if (emailMudou && usuarioRepository.existsByEmail(novoEmail)) {
            throw new EmailJaCadastradoException();
        }
        usuario.setNome(dto.nome().trim());
        if (emailMudou) {
            usuario.alterarEmail(novoEmail);
        }
        try {
            usuarioRepository.saveAndFlush(usuario);
        } catch (DataIntegrityViolationException excecao) {
            throw new EmailJaCadastradoException();
        }
        // O JWT usa o e-mail como assunto: após a troca, o token antigo deixa de resolver o usuário.
        return montarPerfil(usuario, emailMudou ? tokenService.gerarToken(usuario) : null);
    }

    /** Troca a senha de quem está logada. Exige a senha atual, então um token roubado não basta. */
    @Transactional
    public TokenRespostaDTO trocarSenha(Long usuarioId, TrocaSenhaDTO dto) {
        com.glowup.api.dto.RegrasSenha.validarLimiteBcrypt(dto.novaSenha());
        Usuario usuario = buscarUsuario(usuarioId);
        if (!passwordEncoder.matches(dto.senhaAtual(), usuario.getSenhaHash())) {
            throw new IllegalArgumentException("A senha atual está incorreta.");
        }
        if (passwordEncoder.matches(dto.novaSenha(), usuario.getSenhaHash())) {
            throw new IllegalArgumentException("A nova senha deve ser diferente da atual.");
        }
        usuario.setSenhaHash(passwordEncoder.encode(dto.novaSenha()));
        usuario.setSenhaProvisoria(false);
        usuarioRepository.save(usuario);
        // Todas as sessões anteriores (access e refresh tokens) deixam de valer; esta continua com tokens novos.
        refreshTokenService.revogarTodos(usuario.getId());
        return refreshTokenService.iniciarSessao(usuario);
    }

    /** Exclusão lógica da conta (RNF02 / seção 6.16): os pedidos e o histórico financeiro permanecem. */
    @Transactional
    public void excluirConta(Long usuarioId, String senha) {
        Usuario usuario = buscarUsuario(usuarioId);
        if (usuario.getRole() == Role.ADMIN) {
            throw new ConflitoDeEstadoException("A conta da administradora não pode ser excluída pela API.");
        }
        if (senha == null || !passwordEncoder.matches(senha, usuario.getSenhaHash())) {
            throw new IllegalArgumentException("A senha informada está incorreta.");
        }
        if (pedidoRepository.existsByClienteIdAndStatusIn(usuarioId, STATUS_PEDIDO_EM_ABERTO)) {
            throw new ConflitoDeEstadoException(
                    "Você tem pedido em andamento. Aguarde a entrega ou o cancelamento para excluir a conta.");
        }
        for (Endereco endereco : enderecoRepository.findByUsuarioIdOrderByPrincipalDescIdAsc(usuarioId)) {
            endereco.setAtivo(false);
            enderecoRepository.save(endereco);
        }
        usuario.getTelefones().clear();
        usuario.setNome("Conta removida");
        usuario.setEmail("conta-removida-" + usuario.getId() + "@inativo.glowup.invalid");
        usuario.setDataNascimento(java.time.LocalDate.of(1970, 1, 1));
        usuario.setSenhaHash(java.util.UUID.randomUUID().toString());
        usuario.setAtivo(false);
        usuarioRepository.save(usuario);
        refreshTokenService.revogarTodos(usuarioId);
    }

    // ------------------------------------------------------------------ telefones

    @Transactional
    public List<TelefoneRespostaDTO> adicionarTelefone(Long usuarioId, String numeroInformado) {
        Usuario usuario = buscarUsuario(usuarioId);
        String numero = somenteDigitos(numeroInformado);
        if (usuario.getTelefones().size() >= MAXIMO_TELEFONES) {
            throw new IllegalArgumentException("Limite de " + MAXIMO_TELEFONES + " telefones por conta.");
        }
        boolean repetido = usuario.getTelefones().stream().anyMatch(t -> t.getNumero().equals(numero));
        if (repetido) {
            throw new IllegalArgumentException("Este telefone já está cadastrado.");
        }
        usuario.getTelefones().add(Telefone.builder()
                .numero(numero)
                .principal(usuario.getTelefones().isEmpty())
                .usuario(usuario)
                .build());
        usuarioRepository.saveAndFlush(usuario);
        return listarTelefones(usuario);
    }

    @Transactional
    public List<TelefoneRespostaDTO> removerTelefone(Long usuarioId, Long telefoneId) {
        Usuario usuario = buscarUsuario(usuarioId);
        Telefone alvo = buscarTelefone(usuario, telefoneId);
        if (usuario.getTelefones().size() <= 1) {
            throw new IllegalArgumentException("É obrigatório manter pelo menos um telefone.");
        }
        boolean eraPrincipal = alvo.isPrincipal();
        usuario.getTelefones().remove(alvo);
        usuarioRepository.saveAndFlush(usuario);
        if (eraPrincipal) {
            usuario.getTelefones().get(0).setPrincipal(true);
            usuarioRepository.saveAndFlush(usuario);
        }
        return listarTelefones(usuario);
    }

    @Transactional
    public List<TelefoneRespostaDTO> definirTelefonePrincipal(Long usuarioId, Long telefoneId) {
        Usuario usuario = buscarUsuario(usuarioId);
        Telefone alvo = buscarTelefone(usuario, telefoneId);
        // O banco permite um único principal por cliente: desmarca antes de marcar o novo.
        usuario.getTelefones().forEach(t -> t.setPrincipal(false));
        usuarioRepository.saveAndFlush(usuario);
        alvo.setPrincipal(true);
        usuarioRepository.saveAndFlush(usuario);
        return listarTelefones(usuario);
    }

    // ------------------------------------------------------------------ endereços

    @Transactional(readOnly = true)
    public List<EnderecoRespostaDTO> listarEnderecos(Long usuarioId) {
        return enderecoRepository.findByUsuarioIdOrderByPrincipalDescIdAsc(usuarioId).stream()
                .map(EnderecoRespostaDTO::de)
                .toList();
    }

    @Transactional
    public EnderecoRespostaDTO criarEndereco(Long usuarioId, EnderecoRequisicaoDTO dto) {
        Usuario usuario = buscarUsuario(usuarioId);
        List<Endereco> existentes = enderecoRepository.findByUsuarioIdOrderByPrincipalDescIdAsc(usuarioId).stream()
                .filter(Endereco::isAtivo)
                .toList();
        boolean principal = existentes.isEmpty() || Boolean.TRUE.equals(dto.principal());
        if (principal) {
            desmarcarPrincipais(existentes);
        }
        Endereco endereco = Endereco.builder().usuario(usuario).principal(principal).build();
        aplicarDados(endereco, dto);
        return EnderecoRespostaDTO.de(enderecoRepository.save(endereco));
    }

    @Transactional
    public EnderecoRespostaDTO atualizarEndereco(Long usuarioId, Long enderecoId, EnderecoRequisicaoDTO dto) {
        Endereco endereco = buscarEndereco(usuarioId, enderecoId);
        if (Boolean.TRUE.equals(dto.principal()) && !endereco.isPrincipal()) {
            desmarcarPrincipais(enderecoRepository.findByUsuarioIdOrderByPrincipalDescIdAsc(usuarioId));
            endereco.setPrincipal(true);
        }
        aplicarDados(endereco, dto);
        return EnderecoRespostaDTO.de(enderecoRepository.save(endereco));
    }

    @Transactional
    public EnderecoRespostaDTO definirEnderecoPrincipal(Long usuarioId, Long enderecoId) {
        Endereco endereco = buscarEndereco(usuarioId, enderecoId);
        desmarcarPrincipais(enderecoRepository.findByUsuarioIdOrderByPrincipalDescIdAsc(usuarioId));
        endereco.setPrincipal(true);
        return EnderecoRespostaDTO.de(enderecoRepository.save(endereco));
    }

    /** Exclusão lógica; se era o principal, o endereço mais antigo restante assume. */
    @Transactional
    public void excluirEndereco(Long usuarioId, Long enderecoId) {
        Endereco endereco = buscarEndereco(usuarioId, enderecoId);
        boolean eraPrincipal = endereco.isPrincipal();
        endereco.setPrincipal(false);
        endereco.setAtivo(false);
        enderecoRepository.saveAndFlush(endereco);
        if (eraPrincipal) {
            enderecoRepository.findByUsuarioIdOrderByPrincipalDescIdAsc(usuarioId).stream().findFirst()
                    .ifPresent(proximo -> {
                        proximo.setPrincipal(true);
                        enderecoRepository.save(proximo);
                    });
        }
    }

    // ------------------------------------------------------------------ internos

    private void desmarcarPrincipais(List<Endereco> enderecos) {
        boolean alterou = false;
        for (Endereco existente : enderecos) {
            if (existente.isPrincipal()) {
                existente.setPrincipal(false);
                alterou = true;
            }
        }
        if (alterou) {
            enderecoRepository.saveAllAndFlush(enderecos);
        }
    }

    private void aplicarDados(Endereco endereco, EnderecoRequisicaoDTO dto) {
        endereco.setCep(dto.cep().replaceAll("\\D", ""));
        endereco.setLogradouro(dto.logradouro().trim());
        endereco.setNumero(dto.numero().trim());
        endereco.setComplemento(dto.complemento() == null || dto.complemento().isBlank() ? null : dto.complemento().trim());
        endereco.setBairro(dto.bairro().trim());
        endereco.setCidade(dto.cidade().trim());
        endereco.setUf(dto.uf().trim().toUpperCase(Locale.ROOT));
    }

    private Usuario buscarUsuario(Long usuarioId) {
        return usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
    }

    private Telefone buscarTelefone(Usuario usuario, Long telefoneId) {
        return usuario.getTelefones().stream()
                .filter(t -> t.getId().equals(telefoneId))
                .findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException("Telefone não encontrado."));
    }

    private Endereco buscarEndereco(Long usuarioId, Long enderecoId) {
        return enderecoRepository.findByIdAndUsuarioId(enderecoId, usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Endereço não encontrado."));
    }

    private String somenteDigitos(String numero) {
        String digitos = numero == null ? "" : numero.replaceAll("\\D", "");
        if (digitos.length() < 10 || digitos.length() > 13) {
            throw new IllegalArgumentException("Telefone inválido. Informe DDD + número.");
        }
        return digitos;
    }

    private List<TelefoneRespostaDTO> listarTelefones(Usuario usuario) {
        return usuario.getTelefones().stream()
                .map(t -> new TelefoneRespostaDTO(t.getId(), t.getNumero(), t.isPrincipal()))
                .toList();
    }

    private PerfilRespostaDTO montarPerfil(Usuario usuario, String novoToken) {
        return new PerfilRespostaDTO(
                usuario.getId(),
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getDataNascimento(),
                listarTelefones(usuario),
                listarEnderecos(usuario.getId()),
                novoToken,
                usuario.isSenhaProvisoria());
    }
}
