package com.orderflow.auth.domain;

import com.orderflow.shared.persistence.BaseAuditableEntity;
import com.orderflow.shared.security.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entidade de Usuário do Ecossistema OrderFlow com suporte a:
 * <ul>
 *   <li>Chave primária UUIDv7 em {@code BINARY(16)}</li>
 *   <li>Isolamento multi-loja ({@code loja_id} NOT NULL)</li>
 *   <li>Autenticação por email/senha (BCrypt)</li>
 *   <li>RBAC ({@code ATENDENTE}, {@code COZINHA}, {@code GERENTE}, {@code ADMIN})</li>
 *   <li>Hash opcional de PIN de alçada (4 a 6 dígitos) para aprovação de sangrias e cancelamentos</li>
 * </ul>
 */
@Entity
@Table(
        name = "usuarios",
        uniqueConstraints = @UniqueConstraint(name = "uk_usuarios_loja_email", columnNames = {"loja_id", "email"})
)
public class Usuario extends BaseAuditableEntity {

    @Column(name = "nome", nullable = false, length = 100)
    private String nome;

    @Column(name = "email", nullable = false, length = 100)
    private String email;

    @Column(name = "senha_hash", nullable = false, length = 255)
    private String senhaHash;

    @Column(name = "pin_alcada_hash", length = 255)
    private String pinAlcadaHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    @Column(name = "ultimo_login_em")
    private Instant ultimoLoginEm;

    protected Usuario() {
    }

    public Usuario(
            UUID lojaId,
            String nome,
            String email,
            String senhaHash,
            Role role,
            String pinAlcadaHash
    ) {
        setLojaId(lojaId);
        this.nome = Objects.requireNonNull(nome, "nome é obrigatório").trim();
        this.email = normalizeEmail(email);
        this.senhaHash = Objects.requireNonNull(senhaHash, "senhaHash é obrigatório");
        this.role = Objects.requireNonNull(role, "role é obrigatória");
        this.pinAlcadaHash = pinAlcadaHash;
        this.ativo = true;
    }

    public static String normalizeEmail(String email) {
        Objects.requireNonNull(email, "email é obrigatório");
        return email.trim().toLowerCase();
    }

    public boolean possuiAlcadaGerencial() {
        return this.ativo
                && (this.role == Role.GERENTE || this.role == Role.ADMIN)
                && this.pinAlcadaHash != null
                && !this.pinAlcadaHash.isBlank();
    }

    public void registrarLogin(Instant instante) {
        this.ultimoLoginEm = instante;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = Objects.requireNonNull(nome).trim();
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = normalizeEmail(email);
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    public void setSenhaHash(String senhaHash) {
        this.senhaHash = Objects.requireNonNull(senhaHash);
    }

    public String getPinAlcadaHash() {
        return pinAlcadaHash;
    }

    public void setPinAlcadaHash(String pinAlcadaHash) {
        this.pinAlcadaHash = pinAlcadaHash;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = Objects.requireNonNull(role);
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }

    public Instant getUltimoLoginEm() {
        return ultimoLoginEm;
    }
}
