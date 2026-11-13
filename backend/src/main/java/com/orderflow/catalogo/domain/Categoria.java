package com.orderflow.catalogo.domain;

import com.orderflow.shared.persistence.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.text.Normalizer;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Categoria ou subcategoria do cardápio ({@code categorias}).
 * Profundidade máxima: 2 níveis (Categoria Pai ➔ Subcategoria).
 */
@Entity
@Table(name = "categorias")
public class Categoria extends BaseAuditableEntity {

    @Column(name = "parent_id", columnDefinition = "BINARY(16)")
    private UUID categoriaPaiId;

    @Column(name = "nome", nullable = false, length = 60)
    private String nome;

    @Column(name = "slug", nullable = false, length = 60)
    private String slug;

    @Column(name = "ordem", nullable = false)
    private int ordemExibicao = 0;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "estacao_preparo_padrao", nullable = false, length = 15)
    private EstacaoPreparo estacaoPreparoPadrao = EstacaoPreparo.COZINHA;

    @Column(name = "arquivado_em")
    private Instant arquivadoEm;

    protected Categoria() {
    }

    public Categoria(
            UUID lojaId,
            UUID categoriaPaiId,
            String nome,
            String slug,
            int ordemExibicao,
            boolean ativo,
            EstacaoPreparo estacaoPreparoPadrao
    ) {
        setLojaId(lojaId);
        this.categoriaPaiId = categoriaPaiId;
        this.nome = Objects.requireNonNull(nome, "nome é obrigatório").trim();
        this.slug = normalizeSlug(slug != null && !slug.isBlank() ? slug : this.nome);
        this.ordemExibicao = Math.max(0, ordemExibicao);
        this.ativo = ativo;
        this.estacaoPreparoPadrao = estacaoPreparoPadrao != null ? estacaoPreparoPadrao : EstacaoPreparo.COZINHA;
    }

    public static String normalizeSlug(String raw) {
        Objects.requireNonNull(raw, "slug não pode ser nulo");
        String normalized = Normalizer.normalize(raw.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return normalized.length() > 60 ? normalized.substring(0, 60) : normalized;
    }

    public boolean isArquivada() {
        return this.arquivadoEm != null;
    }

    public void arquivar(Instant instante) {
        this.arquivadoEm = instante != null ? instante : Instant.now();
        this.ativo = false;
    }

    public UUID getCategoriaPaiId() {
        return categoriaPaiId;
    }

    public void setCategoriaPaiId(UUID categoriaPaiId) {
        this.categoriaPaiId = categoriaPaiId;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = Objects.requireNonNull(nome).trim();
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = normalizeSlug(slug);
    }

    public int getOrdemExibicao() {
        return ordemExibicao;
    }

    public void setOrdemExibicao(int ordemExibicao) {
        this.ordemExibicao = Math.max(0, ordemExibicao);
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }

    public EstacaoPreparo getEstacaoPreparoPadrao() {
        return estacaoPreparoPadrao;
    }

    public void setEstacaoPreparoPadrao(EstacaoPreparo estacaoPreparoPadrao) {
        this.estacaoPreparoPadrao = estacaoPreparoPadrao != null ? estacaoPreparoPadrao : EstacaoPreparo.COZINHA;
    }

    public Instant getArquivadoEm() {
        return arquivadoEm;
    }
}
