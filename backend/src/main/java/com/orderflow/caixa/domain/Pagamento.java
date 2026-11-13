package com.orderflow.caixa.domain;

import com.orderflow.shared.persistence.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Transação financeira de pagamento vinculada a um pedido e a uma sessão de caixa ({@code pagamentos}).
 * Regra contábil de gaveta: apenas pagamentos em {@code DINHEIRO} geram linha física em {@code movimentacoes_caixa}.
 */
@Entity
@Table(name = "pagamentos")
public class Pagamento extends BaseAuditableEntity {

    @Column(name = "pedido_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID pedidoId;

    @Column(name = "sessao_caixa_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID sessaoCaixaId;

    @Column(name = "idempotency_key", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "metodo", nullable = false, updatable = false, length = 15)
    private MetodoPagamento metodo;

    @Column(name = "valor_centavos", nullable = false, updatable = false)
    private long valorCentavos;

    @Column(name = "valor_recebido_centavos", updatable = false)
    private Long valorRecebidoCentavos;

    @Column(name = "troco_centavos", updatable = false)
    private Long trocoCentavos;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private StatusPagamento status = StatusPagamento.CONFIRMADO;

    @Column(name = "nsu", updatable = false, length = 40)
    private String nsu;

    @Column(name = "autorizacao", updatable = false, length = 40)
    private String autorizacao;

    @Column(name = "txid_pix", updatable = false, length = 64)
    private String txidPix;

    @Column(name = "motivo_estorno", length = 200)
    private String motivoEstorno;

    @Column(name = "estornado_em")
    private Instant estornadoEm;

    @Column(name = "estornado_por", columnDefinition = "BINARY(16)")
    private UUID estornadoPor;

    protected Pagamento() {
    }

    public Pagamento(
            UUID lojaId,
            UUID pedidoId,
            UUID sessaoCaixaId,
            UUID idempotencyKey,
            MetodoPagamento metodo,
            long valorCentavos,
            Long valorRecebidoCentavos,
            Long trocoCentavos,
            String nsu,
            String autorizacao,
            String txidPix
    ) {
        setLojaId(lojaId);
        this.pedidoId = Objects.requireNonNull(pedidoId);
        this.sessaoCaixaId = Objects.requireNonNull(sessaoCaixaId);
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey);
        this.metodo = Objects.requireNonNull(metodo);
        this.valorCentavos = valorCentavos;
        this.valorRecebidoCentavos = valorRecebidoCentavos;
        this.trocoCentavos = trocoCentavos;
        this.nsu = nsu;
        this.autorizacao = autorizacao;
        this.txidPix = txidPix;
        this.status = StatusPagamento.CONFIRMADO;
    }

    public boolean isConfirmado() {
        return this.status == StatusPagamento.CONFIRMADO;
    }

    public void estornar(UUID usuarioId, String motivo, Instant instante) {
        this.status = StatusPagamento.ESTORNADO;
        this.estornadoPor = Objects.requireNonNull(usuarioId);
        this.motivoEstorno = Objects.requireNonNull(motivo).trim();
        this.estornadoEm = instante != null ? instante : Instant.now();
    }

    public UUID getPedidoId() {
        return pedidoId;
    }

    public UUID getSessaoCaixaId() {
        return sessaoCaixaId;
    }

    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }

    public MetodoPagamento getMetodo() {
        return metodo;
    }

    public long getValorCentavos() {
        return valorCentavos;
    }

    public Long getValorRecebidoCentavos() {
        return valorRecebidoCentavos;
    }

    public Long getTrocoCentavos() {
        return trocoCentavos;
    }

    public StatusPagamento getStatus() {
        return status;
    }

    public String getNsu() {
        return nsu;
    }

    public String getAutorizacao() {
        return autorizacao;
    }

    public String getTxidPix() {
        return txidPix;
    }

    public String getMotivoEstorno() {
        return motivoEstorno;
    }

    public Instant getEstornadoEm() {
        return estornadoEm;
    }

    public UUID getEstornadoPor() {
        return estornadoPor;
    }
}
