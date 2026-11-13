package com.orderflow.pedido.domain;

import com.orderflow.shared.id.UuidV7Generator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento imutável de transição do pedido ({@code pedido_eventos} — Transactional Outbox + Replay KDS).
 * Possui sequência monotônica global ({@code seq BIGINT AUTO_INCREMENT}) para reconexão sem perda de mensagens.
 */
@Entity
@Immutable
@Table(name = "pedido_eventos")
public class PedidoEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "seq", nullable = false, updatable = false)
    private Long seq;

    @Column(name = "id", nullable = false, unique = true, updatable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "loja_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID lojaId;

    @Column(name = "pedido_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID pedidoId;

    @Column(name = "tipo", nullable = false, updatable = false, length = 30)
    private String tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_anterior", updatable = false, length = 25)
    private StatusPedido statusAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_novo", nullable = false, updatable = false, length = 25)
    private StatusPedido statusNovo;

    @Column(name = "versao_pedido", nullable = false, updatable = false)
    private int versaoPedido;

    @Column(name = "motivo", updatable = false, length = 200)
    private String motivo;

    @Column(name = "payload_json", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", updatable = false, columnDefinition = "BINARY(16)")
    private UUID createdBy;

    protected PedidoEvento() {
    }

    public PedidoEvento(
            UUID lojaId,
            UUID pedidoId,
            String tipo,
            StatusPedido statusAnterior,
            StatusPedido statusNovo,
            int versaoPedido,
            String motivo,
            String payloadJson,
            UUID createdBy
    ) {
        this.id = UuidV7Generator.nextId();
        this.lojaId = Objects.requireNonNull(lojaId);
        this.pedidoId = Objects.requireNonNull(pedidoId);
        this.tipo = Objects.requireNonNull(tipo);
        this.statusAnterior = statusAnterior;
        this.statusNovo = Objects.requireNonNull(statusNovo);
        this.versaoPedido = versaoPedido;
        this.motivo = motivo;
        this.payloadJson = Objects.requireNonNull(payloadJson);
        this.createdAt = Instant.now();
        this.createdBy = createdBy;
    }

    @PrePersist
    protected void prePersist() {
        if (this.id == null) {
            this.id = UuidV7Generator.nextId();
        }
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    public Long getSeq() {
        return seq;
    }

    public UUID getId() {
        return id;
    }

    public UUID getLojaId() {
        return lojaId;
    }

    public UUID getPedidoId() {
        return pedidoId;
    }

    public String getTipo() {
        return tipo;
    }

    public StatusPedido getStatusAnterior() {
        return statusAnterior;
    }

    public StatusPedido getStatusNovo() {
        return statusNovo;
    }

    public int getVersaoPedido() {
        return versaoPedido;
    }

    public String getMotivo() {
        return motivo;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }
}
