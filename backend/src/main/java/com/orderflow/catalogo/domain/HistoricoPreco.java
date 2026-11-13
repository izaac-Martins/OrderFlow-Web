package com.orderflow.catalogo.domain;

import com.orderflow.shared.persistence.BaseImmutableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Trilha imutável de alterações de preço de Produtos e Modificadores ({@code historico_precos}).
 */
@Entity
@Table(name = "historico_precos")
public class HistoricoPreco extends BaseImmutableEntity {

    @Column(name = "entidade_tipo", nullable = false, updatable = false, length = 15)
    private String entidadeTipo;

    @Column(name = "entidade_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID entidadeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_preco_anterior", updatable = false, length = 15)
    private TipoPreco tipoPrecoAnterior;

    @Column(name = "valor_anterior", updatable = false)
    private Long valorAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_preco_novo", nullable = false, updatable = false, length = 15)
    private TipoPreco tipoPrecoNovo;

    @Column(name = "valor_novo", nullable = false, updatable = false)
    private long valorNovo;

    @Column(name = "motivo", nullable = false, updatable = false, length = 200)
    private String motivo;

    @Column(name = "vigente_desde", nullable = false, updatable = false)
    private Instant vigenteDesde;

    protected HistoricoPreco() {
    }

    public HistoricoPreco(
            UUID lojaId,
            String entidadeTipo,
            UUID entidadeId,
            TipoPreco tipoPrecoAnterior,
            Long valorAnterior,
            TipoPreco tipoPrecoNovo,
            long valorNovo,
            String motivo
    ) {
        setLojaId(lojaId);
        this.entidadeTipo = Objects.requireNonNull(entidadeTipo);
        this.entidadeId = Objects.requireNonNull(entidadeId);
        this.tipoPrecoAnterior = tipoPrecoAnterior;
        this.valorAnterior = valorAnterior;
        this.tipoPrecoNovo = Objects.requireNonNull(tipoPrecoNovo);
        this.valorNovo = valorNovo;
        this.motivo = Objects.requireNonNull(motivo).trim();
        this.vigenteDesde = Instant.now();
    }

    public String getEntidadeTipo() {
        return entidadeTipo;
    }

    public UUID getEntidadeId() {
        return entidadeId;
    }

    public TipoPreco getTipoPrecoAnterior() {
        return tipoPrecoAnterior;
    }

    public Long getValorAnterior() {
        return valorAnterior;
    }

    public TipoPreco getTipoPrecoNovo() {
        return tipoPrecoNovo;
    }

    public long getValorNovo() {
        return valorNovo;
    }

    public String getMotivo() {
        return motivo;
    }

    public Instant getVigenteDesde() {
        return vigenteDesde;
    }
}
