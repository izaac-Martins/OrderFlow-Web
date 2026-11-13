package com.orderflow.catalogo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Tabela de vínculo N:N entre {@link Produto} e {@link GrupoModificador} ({@code produto_grupos_modificadores}),
 * suportando overrides opcionais de mínimo e máximo de seleção.
 */
@Entity
@Table(name = "produto_grupos_modificadores")
public class ProdutoModificadorGrupo {

    @EmbeddedId
    private Id id;

    @Column(name = "loja_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID lojaId;

    @Column(name = "ordem", nullable = false)
    private int ordem = 0;

    @Column(name = "min_selecao_override")
    private Integer minSelecaoOverride;

    @Column(name = "max_selecao_override")
    private Integer maxSelecaoOverride;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ProdutoModificadorGrupo() {
    }

    public ProdutoModificadorGrupo(
            UUID lojaId,
            UUID produtoId,
            UUID grupoId,
            int ordem,
            Integer minSelecaoOverride,
            Integer maxSelecaoOverride
    ) {
        this.id = new Id(produtoId, grupoId);
        this.lojaId = Objects.requireNonNull(lojaId, "lojaId é obrigatório");
        this.ordem = Math.max(0, ordem);
        this.minSelecaoOverride = minSelecaoOverride;
        this.maxSelecaoOverride = maxSelecaoOverride;
        this.createdAt = Instant.now();
    }

    @PrePersist
    protected void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    public int calcularMinSelecaoEfetiva(GrupoModificador grupo) {
        return this.minSelecaoOverride != null ? this.minSelecaoOverride : grupo.getMinSelecao();
    }

    public int calcularMaxSelecaoEfetiva(GrupoModificador grupo) {
        return this.maxSelecaoOverride != null ? this.maxSelecaoOverride : grupo.getMaxSelecao();
    }

    public UUID getProdutoId() {
        return id.getProdutoId();
    }

    public UUID getGrupoId() {
        return id.getGrupoId();
    }

    public UUID getLojaId() {
        return lojaId;
    }

    public int getOrdem() {
        return ordem;
    }

    public Integer getMinSelecaoOverride() {
        return minSelecaoOverride;
    }

    public Integer getMaxSelecaoOverride() {
        return maxSelecaoOverride;
    }

    @Embeddable
    public static class Id implements Serializable {

        @Column(name = "produto_id", nullable = false, columnDefinition = "BINARY(16)")
        private UUID produtoId;

        @Column(name = "grupo_id", nullable = false, columnDefinition = "BINARY(16)")
        private UUID grupoId;

        protected Id() {
        }

        public Id(UUID produtoId, UUID grupoId) {
            this.produtoId = Objects.requireNonNull(produtoId);
            this.grupoId = Objects.requireNonNull(grupoId);
        }

        public UUID getProdutoId() {
            return produtoId;
        }

        public UUID getGrupoId() {
            return grupoId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Id that)) return false;
            return Objects.equals(produtoId, that.produtoId) && Objects.equals(grupoId, that.grupoId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(produtoId, grupoId);
        }
    }
}
