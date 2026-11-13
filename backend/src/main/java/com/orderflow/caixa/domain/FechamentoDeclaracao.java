package com.orderflow.caixa.domain;

import com.orderflow.shared.persistence.BaseImmutableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.util.Objects;
import java.util.UUID;

/**
 * Declaração imutável por método de pagamento gerada no fechamento cego ({@code fechamento_declaracoes}).
 */
@Entity
@Immutable
@Table(name = "fechamento_declaracoes")
public class FechamentoDeclaracao extends BaseImmutableEntity {

    @Column(name = "sessao_caixa_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID sessaoCaixaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "metodo", nullable = false, updatable = false, length = 15)
    private MetodoPagamento metodo;

    @Column(name = "valor_declarado_centavos", nullable = false, updatable = false)
    private long valorDeclaradoCentavos;

    @Column(name = "valor_esperado_centavos", nullable = false, updatable = false)
    private long valorEsperadoCentavos;

    @Column(name = "diferenca_centavos", nullable = false, updatable = false)
    private long diferencaCentavos;

    @Column(name = "quantidade_comprovantes", updatable = false)
    private Integer quantidadeComprovantes;

    protected FechamentoDeclaracao() {
    }

    public FechamentoDeclaracao(
            UUID lojaId,
            UUID sessaoCaixaId,
            MetodoPagamento metodo,
            long valorDeclaradoCentavos,
            long valorEsperadoCentavos,
            Integer quantidadeComprovantes
    ) {
        setLojaId(lojaId);
        this.sessaoCaixaId = Objects.requireNonNull(sessaoCaixaId);
        this.metodo = Objects.requireNonNull(metodo);
        this.valorDeclaradoCentavos = valorDeclaradoCentavos;
        this.valorEsperadoCentavos = valorEsperadoCentavos;
        this.diferencaCentavos = valorDeclaradoCentavos - valorEsperadoCentavos;
        this.quantidadeComprovantes = quantidadeComprovantes;
    }

    public SituacaoConferencia calcularSituacao() {
        if (this.diferencaCentavos == 0L) {
            return SituacaoConferencia.OK;
        }
        return this.diferencaCentavos < 0L ? SituacaoConferencia.QUEBRA : SituacaoConferencia.SOBRA;
    }

    public UUID getSessaoCaixaId() {
        return sessaoCaixaId;
    }

    public MetodoPagamento getMetodo() {
        return metodo;
    }

    public long getValorDeclaradoCentavos() {
        return valorDeclaradoCentavos;
    }

    public long getValorEsperadoCentavos() {
        return valorEsperadoCentavos;
    }

    public long getDiferencaCentavos() {
        return diferencaCentavos;
    }

    public Integer getQuantidadeComprovantes() {
        return quantidadeComprovantes;
    }
}
