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
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Opção de modificador/adicional dentro de um {@link GrupoModificador} ({@code modificadores}).
 */
@Entity
@Table(name = "modificadores")
public class OpcaoModificador extends BaseAuditableEntity {

    @Column(name = "grupo_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID grupoId;

    @Column(name = "nome", nullable = false, length = 60)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_preco", nullable = false, length = 15)
    private TipoPreco tipoPreco = TipoPreco.FIXO;

    @Column(name = "valor_preco", nullable = false)
    private long valorPreco = 0L;

    @Column(name = "padrao", nullable = false)
    private boolean padrao = false;

    @Column(name = "max_quantidade", nullable = false)
    private int maxQuantidade = 1;

    @Column(name = "ordem", nullable = false)
    private int ordem = 0;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    @Column(name = "pausado", nullable = false)
    private boolean pausado = false;

    @Column(name = "motivo_pausa", length = 200)
    private String motivoPausa;

    @Column(name = "arquivado_em")
    private Instant arquivadoEm;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "modificador_insumos",
            joinColumns = @JoinColumn(name = "modificador_id")
    )
    @Column(name = "insumo_id", nullable = false, columnDefinition = "BINARY(16)")
    private Set<UUID> insumoIds = new HashSet<>();

    protected OpcaoModificador() {
    }

    public OpcaoModificador(
            UUID lojaId,
            UUID grupoId,
            String nome,
            TipoPreco tipoPreco,
            long valorPreco,
            boolean padrao,
            int maxQuantidade,
            int ordem,
            boolean ativo
    ) {
        setLojaId(lojaId);
        this.grupoId = Objects.requireNonNull(grupoId, "grupoId é obrigatório");
        this.nome = Objects.requireNonNull(nome, "nome é obrigatório").trim();
        this.tipoPreco = tipoPreco != null ? tipoPreco : TipoPreco.FIXO;
        this.valorPreco = this.tipoPreco == TipoPreco.NENHUM ? 0L : valorPreco;
        this.padrao = padrao;
        this.maxQuantidade = Math.max(1, maxQuantidade);
        this.ordem = Math.max(0, ordem);
        this.ativo = ativo;
    }

    /**
     * Alias semântico para {@code preco_adicional_centavos} quando {@code tipoPreco = FIXO}.
     */
    public long getPrecoAdicionalCentavos() {
        return this.tipoPreco == TipoPreco.FIXO ? this.valorPreco : 0L;
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

    public UUID getGrupoId() {
        return grupoId;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = Objects.requireNonNull(nome).trim();
    }

    public TipoPreco getTipoPreco() {
        return tipoPreco;
    }

    public void setTipoPreco(TipoPreco tipoPreco) {
        this.tipoPreco = Objects.requireNonNull(tipoPreco);
    }

    public long getValorPreco() {
        return valorPreco;
    }

    public void setValorPreco(long valorPreco) {
        this.valorPreco = valorPreco;
    }

    public boolean isPadrao() {
        return padrao;
    }

    public void setPadrao(boolean padrao) {
        this.padrao = padrao;
    }

    public int getMaxQuantidade() {
        return maxQuantidade;
    }

    public void setMaxQuantidade(int maxQuantidade) {
        this.maxQuantidade = Math.max(1, maxQuantidade);
    }

    public int getOrdem() {
        return ordem;
    }

    public void setOrdem(int ordem) {
        this.ordem = Math.max(0, ordem);
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

    public Set<UUID> getInsumoIds() {
        return insumoIds;
    }

    public void setInsumoIds(Set<UUID> insumoIds) {
        this.insumoIds = insumoIds != null ? new HashSet<>(insumoIds) : new HashSet<>();
    }
}
