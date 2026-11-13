package com.orderflow.auth.domain;

import com.orderflow.shared.persistence.BaseImmutableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entidade de Refresh Token rotativo vinculado a dispositivo e loja.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken extends BaseImmutableEntity {

    @Column(name = "usuario_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID usuarioId;

    @Column(name = "token_hash", nullable = false, unique = true, updatable = false, length = 128)
    private String tokenHash;

    @Column(name = "dispositivo", nullable = false, updatable = false, length = 80)
    private String dispositivo;

    @Column(name = "expira_em", nullable = false, updatable = false)
    private Instant expiraEm;

    @Column(name = "revogado_em")
    private Instant revogadoEm;

    protected RefreshToken() {
    }

    public RefreshToken(
            UUID lojaId,
            UUID usuarioId,
            String tokenHash,
            String dispositivo,
            Instant expiraEm
    ) {
        setLojaId(lojaId);
        this.usuarioId = Objects.requireNonNull(usuarioId, "usuarioId é obrigatório");
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash é obrigatório");
        this.dispositivo = Objects.requireNonNull(dispositivo, "dispositivo é obrigatório").trim();
        this.expiraEm = Objects.requireNonNull(expiraEm, "expiraEm é obrigatório");
    }

    public boolean estaAtivoEm(Instant instante) {
        return this.revogadoEm == null && this.expiraEm.isAfter(instante);
    }

    public void revogar(Instant instante) {
        if (this.revogadoEm == null) {
            this.revogadoEm = instante;
        }
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public String getDispositivo() {
        return dispositivo;
    }

    public Instant getExpiraEm() {
        return expiraEm;
    }

    public Instant getRevogadoEm() {
        return revogadoEm;
    }
}
