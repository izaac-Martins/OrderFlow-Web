package com.orderflow.shared.persistence;

import com.orderflow.shared.id.UuidV7Generator;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Superclasse para entidades contábeis e de auditoria estritamente imutáveis (insert-only),
 * como {@code movimentacoes_caixa}, {@code pedido_eventos}, {@code historico_precos} e {@code fechamento_declaracoes}.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseImmutableEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "loja_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID lojaId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false, columnDefinition = "BINARY(16)")
    private UUID createdBy;

    @PrePersist
    protected void prePersistBase() {
        if (this.id == null) {
            this.id = UuidV7Generator.nextId();
        }
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    protected void setId(UUID id) {
        this.id = id;
    }

    public UUID getLojaId() {
        return lojaId;
    }

    protected void setLojaId(UUID lojaId) {
        this.lojaId = Objects.requireNonNull(lojaId, "lojaId é obrigatório");
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }
}
