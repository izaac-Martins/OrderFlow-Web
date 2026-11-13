package com.orderflow.catalogo.domain;

import com.orderflow.shared.persistence.BaseAuditableEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Produto do cardápio ({@code SIMPLES} ou {@code COMBO}) com SKU único por loja
 * e preço base em centavos inteiros ({@code BIGINT}).
 */
@Entity
@Table(name = "produtos")
public class Produto extends BaseAuditableEntity {

    @Column(name = "categoria_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID categoriaId;

    @Column(name = "sku", nullable = false, length = 30)
    private String sku;

    @Column(name = "nome", nullable = false, length = 80)
    private String nome;

    @Column(name = "descricao", length = 500)
    private String descricao;

    @Column(name = "imagem_url", length = 500)
    private String imagemUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 15)
    private TipoProduto tipo = TipoProduto.SIMPLES;

    @Column(name = "preco_base_centavos", nullable = false)
    private long precoBaseCentavos;

    @Enumerated(EnumType.STRING)
    @Column(name = "estacao_preparo", length = 15)
    private EstacaoPreparo estacaoPreparo;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    @Column(name = "pausado", nullable = false)
    private boolean pausado = false;

    @Column(name = "motivo_pausa", length = 200)
    private String motivoPausa;

    @Column(name = "ordem", nullable = false)
    private int ordem = 0;

    @Column(name = "arquivado_em")
    private Instant arquivadoEm;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "produto_insumos",
            joinColumns = @JoinColumn(name = "produto_id")
    )
    @Column(name = "insumo_id", nullable = false, columnDefinition = "BINARY(16)")
    private Set<UUID> insumoIds = new HashSet<>();

    protected Produto() {
    }

    public Produto(
            UUID lojaId,
            UUID categoriaId,
            String sku,
            String nome,
            String descricao,
            String imagemUrl,
            TipoProduto tipo,
            long precoBaseCentavos,
            EstacaoPreparo estacaoPreparo,
            boolean ativo,
            int ordem
    ) {
        setLojaId(lojaId);
        this.categoriaId = Objects.requireNonNull(categoriaId, "categoriaId é obrigatório");
        this.sku = normalizeSku(sku);
        this.nome = Objects.requireNonNull(nome, "nome é obrigatório").trim();
        this.descricao = descricao != null ? descricao.trim() : null;
        this.imagemUrl = imagemUrl != null ? imagemUrl.trim() : null;
        this.tipo = tipo != null ? tipo : TipoProduto.SIMPLES;
        this.precoBaseCentavos = precoBaseCentavos;
        this.estacaoPreparo = estacaoPreparo;
        this.ativo = ativo;
        this.ordem = Math.max(0, ordem);
    }

    public static String normalizeSku(String rawSku) {
        Objects.requireNonNull(rawSku, "sku é obrigatório");
        return rawSku.trim().toUpperCase(Locale.ROOT);
    }

    public boolean isDisponivelProprio() {
        return this.ativo && !this.pausado && this.arquivadoEm == null;
    }

    public void alterarPausa(boolean pausado, String motivo) {
        this.pausado = pausado;
        this.motivoPausa = pausado ? (motivo != null ? motivo.trim() : null) : null;
    }

    public void arquivar(Instant instante) {
        this.arquivadoEm = instante != null ? instante : Instant.now();
        this.ativo = false;
    }

    public boolean isArquivado() {
        return this.arquivadoEm != null;
    }

    public UUID getCategoriaId() {
        return categoriaId;
    }

    public void setCategoriaId(UUID categoriaId) {
        this.categoriaId = Objects.requireNonNull(categoriaId);
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = normalizeSku(sku);
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = Objects.requireNonNull(nome).trim();
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao != null ? descricao.trim() : null;
    }

    public String getImagemUrl() {
        return imagemUrl;
    }

    public void setImagemUrl(String imagemUrl) {
        this.imagemUrl = imagemUrl != null ? imagemUrl.trim() : null;
    }

    public TipoProduto getTipo() {
        return tipo;
    }

    public void setTipo(TipoProduto tipo) {
        this.tipo = Objects.requireNonNull(tipo);
    }

    public long getPrecoBaseCentavos() {
        return precoBaseCentavos;
    }

    public void setPrecoBaseCentavos(long precoBaseCentavos) {
        this.precoBaseCentavos = precoBaseCentavos;
    }

    public EstacaoPreparo getEstacaoPreparo() {
        return estacaoPreparo;
    }

    public void setEstacaoPreparo(EstacaoPreparo estacaoPreparo) {
        this.estacaoPreparo = estacaoPreparo;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }

    public boolean isPausado() {
        return pausado;
    }

    public void setPausado(boolean pausado) {
        this.pausado = pausado;
    }

    public String getMotivoPausa() {
        return motivoPausa;
    }

    public int getOrdem() {
        return ordem;
    }

    public void setOrdem(int ordem) {
        this.ordem = Math.max(0, ordem);
    }

    public Instant getArquivadoEm() {
        return arquivadoEm;
    }

    public Set<UUID> getInsumoIds() {
        return insumoIds;
    }

    public void setInsumoIds(Set<UUID> insumoIds) {
        this.insumoIds = insumoIds != null ? new HashSet<>(insumoIds) : new HashSet<>();
    }
}
