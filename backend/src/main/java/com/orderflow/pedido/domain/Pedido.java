package com.orderflow.pedido.domain;

import com.orderflow.shared.persistence.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Agregado raiz de Pedido ({@code pedidos}) com máquina de estados explícita,
 * valores em centavos inteiros ({@code BIGINT}) e controle de concorrência otimista ({@code @Version}).
 */
@Entity
@Table(name = "pedidos")
public class Pedido extends BaseAuditableEntity {

    @Column(name = "sessao_caixa_id", columnDefinition = "BINARY(16)")
    private UUID sessaoCaixaId;

    @Column(name = "cliente_id", columnDefinition = "BINARY(16)")
    private UUID clienteId;

    @Column(name = "idempotency_key", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID idempotencyKey;

    @Column(name = "numero_diario", nullable = false, updatable = false)
    private int numeroDiario;

    @Column(name = "data_operacional", nullable = false, updatable = false)
    private LocalDate dataOperacional;

    @Enumerated(EnumType.STRING)
    @Column(name = "origem", nullable = false, length = 20)
    private OrigemPedido origem = OrigemPedido.APP_ATENDENTE;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_atendimento", nullable = false, length = 20)
    private TipoAtendimento tipoAtendimento = TipoAtendimento.BALCAO;

    @Enumerated(EnumType.STRING)
    @Column(name = "modalidade_pagamento", nullable = false, length = 15)
    private ModalidadePagamento modalidadePagamento = ModalidadePagamento.PRE_PAGO;

    @Column(name = "identificador_cliente", length = 60)
    private String identificadorCliente;

    @Column(name = "mesa", length = 10)
    private String mesa;

    @Column(name = "observacao", length = 300)
    private String observacao;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 25)
    private StatusPedido status = StatusPedido.CRIADO;

    @Column(name = "subtotal_centavos", nullable = false)
    private long subtotalCentavos;

    @Column(name = "desconto_centavos", nullable = false)
    private long descontoCentavos = 0L;

    @Column(name = "total_centavos", nullable = false)
    private long valorTotalCentavos;

    @Column(name = "total_pago_centavos", nullable = false)
    private long totalPagoCentavos = 0L;

    @Column(name = "saldo_devedor_centavos", nullable = false)
    private long saldoDevedorCentavos;

    @Column(name = "motivo_cancelamento", length = 200)
    private String motivoCancelamento;

    @Column(name = "cancelado_por", columnDefinition = "BINARY(16)")
    private UUID canceladoPor;

    @Column(name = "registrar_perda", nullable = false)
    private boolean registrarPerda = false;

    @Column(name = "fiscal_status", length = 20)
    private String fiscalStatus;

    @Column(name = "confirmado_em")
    private Instant confirmadoEm;

    @Column(name = "preparo_iniciado_em")
    private Instant preparoIniciadoEm;

    @Column(name = "pronto_em")
    private Instant prontoEm;

    @Column(name = "entregue_em")
    private Instant entregueEm;

    protected Pedido() {
    }

    public Pedido(
            UUID lojaId,
            UUID sessaoCaixaId,
            UUID clienteId,
            UUID idempotencyKey,
            int numeroDiario,
            LocalDate dataOperacional,
            OrigemPedido origem,
            TipoAtendimento tipoAtendimento,
            ModalidadePagamento modalidadePagamento,
            String identificadorCliente,
            String mesa,
            String observacao,
            long subtotalCentavos,
            long descontoCentavos
    ) {
        setLojaId(lojaId);
        this.sessaoCaixaId = sessaoCaixaId;
        this.clienteId = clienteId;
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey é obrigatória");
        this.numeroDiario = numeroDiario;
        this.dataOperacional = Objects.requireNonNull(dataOperacional, "dataOperacional é obrigatória");
        this.origem = origem != null ? origem : OrigemPedido.APP_ATENDENTE;
        this.tipoAtendimento = tipoAtendimento != null ? tipoAtendimento : TipoAtendimento.BALCAO;
        this.modalidadePagamento = modalidadePagamento != null ? modalidadePagamento : ModalidadePagamento.PRE_PAGO;
        this.identificadorCliente = identificadorCliente != null ? identificadorCliente.trim() : null;
        this.mesa = mesa != null ? mesa.trim() : null;
        this.observacao = observacao != null ? observacao.trim() : null;
        this.subtotalCentavos = Math.max(0L, subtotalCentavos);
        this.descontoCentavos = Math.max(0L, Math.min(descontoCentavos, this.subtotalCentavos));
        this.valorTotalCentavos = this.subtotalCentavos - this.descontoCentavos;
        this.totalPagoCentavos = 0L;
        this.saldoDevedorCentavos = this.valorTotalCentavos;
        this.status = StatusPedido.CRIADO;
    }

    public void registrarPagamentoConfirmado(long valorPago, UUID sessaoId) {
        if (this.sessaoCaixaId == null) {
            this.sessaoCaixaId = sessaoId;
        }
        this.totalPagoCentavos += valorPago;
        this.saldoDevedorCentavos = Math.max(0L, this.valorTotalCentavos - this.totalPagoCentavos);
    }

    public void reverterPagamentoEstornado(long valorEstornado) {
        this.totalPagoCentavos = Math.max(0L, this.totalPagoCentavos - valorEstornado);
        this.saldoDevedorCentavos = Math.max(0L, this.valorTotalCentavos - this.totalPagoCentavos);
    }

    public boolean isQuitado() {
        return this.saldoDevedorCentavos == 0L;
    }

    public void marcarConfirmado(Instant instante) {
        this.status = StatusPedido.CONFIRMADO;
        if (this.confirmadoEm == null) {
            this.confirmadoEm = instante != null ? instante : Instant.now();
        }
    }

    public void marcarEmPreparo(Instant instante) {
        this.status = StatusPedido.EM_PREPARO;
        if (this.preparoIniciadoEm == null) {
            this.preparoIniciadoEm = instante != null ? instante : Instant.now();
        }
    }

    public void marcarPronto(Instant instante) {
        this.status = StatusPedido.PRONTO;
        this.prontoEm = instante != null ? instante : Instant.now();
    }

    public void marcarRefazer(Instant instante) {
        this.status = StatusPedido.EM_PREPARO;
    }

    public void marcarEntregue(Instant instante) {
        this.status = StatusPedido.ENTREGUE;
        this.entregueEm = instante != null ? instante : Instant.now();
    }

    public void marcarCanceladoOuEstornado(boolean houvePagamentoRevertido, UUID usuarioId, String motivo, boolean registrarPerda) {
        this.status = houvePagamentoRevertido ? StatusPedido.ESTORNADO : StatusPedido.CANCELADO;
        this.canceladoPor = usuarioId;
        this.motivoCancelamento = motivo != null ? motivo.trim() : null;
        this.registrarPerda = registrarPerda;
    }

    public UUID getSessaoCaixaId() {
        return sessaoCaixaId;
    }

    public UUID getClienteId() {
        return clienteId;
    }

    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }

    public int getNumeroDiario() {
        return numeroDiario;
    }

    public LocalDate getDataOperacional() {
        return dataOperacional;
    }

    public OrigemPedido getOrigem() {
        return origem;
    }

    public TipoAtendimento getTipoAtendimento() {
        return tipoAtendimento;
    }

    public ModalidadePagamento getModalidadePagamento() {
        return modalidadePagamento;
    }

    public String getIdentificadorCliente() {
        return identificadorCliente;
    }

    public String getMesa() {
        return mesa;
    }

    public String getObservacao() {
        return observacao;
    }

    public StatusPedido getStatus() {
        return status;
    }

    public long getSubtotalCentavos() {
        return subtotalCentavos;
    }

    public long getDescontoCentavos() {
        return descontoCentavos;
    }

    public long getValorTotalCentavos() {
        return valorTotalCentavos;
    }

    public long getTotalPagoCentavos() {
        return totalPagoCentavos;
    }

    public long getSaldoDevedorCentavos() {
        return saldoDevedorCentavos;
    }

    public String getMotivoCancelamento() {
        return motivoCancelamento;
    }

    public UUID getCanceladoPor() {
        return canceladoPor;
    }

    public boolean isRegistrarPerda() {
        return registrarPerda;
    }

    public String getFiscalStatus() {
        return fiscalStatus;
    }

    public void setFiscalStatus(String fiscalStatus) {
        this.fiscalStatus = fiscalStatus;
    }

    public Instant getConfirmadoEm() {
        return confirmadoEm;
    }

    public Instant getPreparoIniciadoEm() {
        return preparoIniciadoEm;
    }

    public Instant getProntoEm() {
        return prontoEm;
    }

    public Instant getEntregueEm() {
        return entregueEm;
    }
}
