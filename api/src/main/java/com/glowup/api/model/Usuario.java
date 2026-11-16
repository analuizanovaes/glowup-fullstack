package com.glowup.api.model;

import lombok.Getter;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Conta de acesso. A identidade é o ID numérico autoincremento (RF01); o CPF não pertence à entidade (RD01).
 * A exclusão é sempre lógica: a tabela exige deleted_at preenchido quando ativo = false.
 */

@Entity
@Table(name = "usuarios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE usuarios SET ativo = false, deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("ativo = true")
// Esta entidade pode ser serializada dentro de outras (ex.: Pedido.cliente): o hash da senha nunca sai na API.
@JsonIgnoreProperties({"senhaHash", "password", "username", "authorities", "accountNonExpired",
        "accountNonLocked", "credentialsNonExpired", "enabled", "telefones", "createdAt", "updatedAt", "deletedAt"})
public class Usuario implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(nullable = false)
    private String senhaHash;

    /** true na conta criada com senha inicial: até trocar a senha, a API só libera a própria troca de senha. */
    @Column(name = "senha_provisoria", nullable = false)
    @Builder.Default
    private boolean senhaProvisoria = false;

    @Column(nullable = false)
    private LocalDate dataNascimento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false)
    @Builder.Default
    private boolean ativo = true;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;

    @OneToMany(mappedBy = "usuario", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Telefone> telefones = new ArrayList<>();

    public int calcularIdade() {
        if (this.dataNascimento == null) {
            return 0;
        }
        return Period.between(this.dataNascimento, LocalDate.now()).getYears();
    }

    public void alterarEmail(String novoEmail) {
        this.email = novoEmail;
    }

    /** Mantém a regra da tabela: inativar sempre registra o momento da exclusão lógica. */
    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
        this.deletedAt = ativo ? null : LocalDateTime.now();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (this.role == Role.ADMIN) {
            return List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_CLIENT"));
        }
        return List.of(new SimpleGrantedAuthority("ROLE_CLIENT"));
    }

    @Override
    public String getPassword() {
        return this.senhaHash;
    }

    @Override
    public String getUsername() {
        return this.email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        // Bloqueia o login caso a conta tenha sofrido Soft Delete (ativo = false)
        return this.ativo;
    }
}
