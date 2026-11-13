package com.orderflow.catalogo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Contador monotônico de versão do catálogo por loja ({@code catalogo_versao}).
 * Base para ETag de {@code GET /api/v1/catalogo} e sincronização STOMP.
 */
@Entity
@Table(name = "catalogo_versao")
public class CatalogoVersao {

    @Id
    @Column(name = "loja_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID lojaId;

    @Column(name = "versao", nullable = false)
    private long versao = 1L;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected CatalogoVersao() {
    }

    public CatalogoVersao(UUID lojaId, long versao) {
        this.lojaId = Objects.requireNonNull(lojaId, "lojaId é obrigatório");
        this.versao = versao;
        this.atualizadoEm = Instant.now();
    }

    @PrePersist
    @PreUpdate
    protected void touch() {
        this.atualizadoEm = Instant.now();
    }

    public long incrementar() {
        this.versao += 1L;
        this.atualizadoEm = Instant.now();
        return this.versao;
    }

    public UUID getLojaId() {
        return lojaId;
    }

    public long getVersao() {
        return versao;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }
}
