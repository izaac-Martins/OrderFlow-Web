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
 * Movimentação física de caixa ({@code movimentacoes_caixa}).
 * Estritamente imutável (insert-only, anotada com {@link Immutable} e protegida por triggers SQL).
 */
@Entity
@Immutable
@Table(name = "movimentacoes_caixa")
public class MovimentacaoCaixa extends BaseImmutableEntity {

    @Column(name = "sessao_caixa_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID sessaoCaixaId;

    @Column(name = "idempotency_key", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, updatable = false, length = 30)
    private TipoMovimentacao tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "natureza", nullable = false, updatable = false, length = 10)
    private NaturezaMovimentacao natureza;

    @Column(name = "valor_centavos", nullable = false, updatable = false)
    private long valorCentavos;

    @Column(name = "motivo", updatable = false, length = 200)
    private String motivo;

    @Column(name = "pagamento_id", updatable = false, columnDefinition = "BINARY(16)")
    private UUID pagamentoId;

    @Column(name = "movimentacao_estornada_id", updatable = false, columnDefinition = "BINARY(16)")
    private UUID movimentacaoEstornadaId;

    @Column(name = "aprovado_por", updatable = false, columnDefinition = "BINARY(16)")
    private UUID usuarioAprovadorId;

    @Column(name = "sinalizacao", updatable = false, length = 40)
    private String sinalizacao;

    protected MovimentacaoCaixa() {
    }

    public MovimentacaoCaixa(
            UUID lojaId,
            UUID sessaoCaixaId,
            UUID idempotencyKey,
            TipoMovimentacao tipo,
            NaturezaMovimentacao natureza,
            long valorCentavos,
            String motivo,
            UUID pagamentoId,
            UUID movimentacaoEstornadaId,
            UUID usuarioAprovadorId,
            String sinalizacao
    ) {
        setLojaId(lojaId);
        this.sessaoCaixaId = Objects.requireNonNull(sessaoCaixaId, "sessaoCaixaId é obrigatório");
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey é obrigatória");
        this.tipo = Objects.requireNonNull(tipo, "tipo é obrigatório");
        this.natureza = natureza != null ? natureza : tipo.getNaturezaPadrao();
        this.valorCentavos = valorCentavos;
        this.motivo = motivo != null ? motivo.trim() : null;
        this.pagamentoId = pagamentoId;
        this.movimentacaoEstornadaId = movimentacaoEstornadaId;
        this.usuarioAprovadorId = usuarioAprovadorId;
        this.sinalizacao = sinalizacao;
    }

    public long valorComSinal() {
        return this.natureza == NaturezaMovimentacao.ENTRADA ? this.valorCentavos : -this.valorCentavos;
    }

    public UUID getSessaoCaixaId() {
        return sessaoCaixaId;
    }

    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }

    public TipoMovimentacao getTipo() {
        return tipo;
    }

    public NaturezaMovimentacao getNatureza() {
        return natureza;
    }

    public long getValorCentavos() {
        return valorCentavos;
    }

    public String getMotivo() {
        return motivo;
    }

    public UUID getPagamentoId() {
        return pagamentoId;
    }

    public UUID getMovimentacaoEstornadaId() {
        return movimentacaoEstornadaId;
    }

    public UUID getUsuarioAprovadorId() {
        return usuarioAprovadorId;
    }

    public String getSinalizacao() {
        return sinalizacao;
    }
}
