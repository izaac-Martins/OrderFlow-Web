package com.orderflow.shared.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.Instant;
import java.util.UUID;

/**
 * Superclasse para agregados mutáveis com controle de concorrência otimista ({@code @Version})
 * e trilha completa de auditoria ({@code created_at}, {@code updated_at}, {@code created_by}, {@code updated_by}).
 */
@MappedSuperclass
public abstract class BaseAuditableEntity extends BaseImmutableEntity {

    @Version
    @Column(name = "versao", nullable = false)
    private Integer versao = 1;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by", columnDefinition = "BINARY(16)")
    private UUID updatedBy;

    public Integer getVersao() {
        return versao;
    }

    public void setVersao(Integer versao) {
        this.versao = versao;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }
}
