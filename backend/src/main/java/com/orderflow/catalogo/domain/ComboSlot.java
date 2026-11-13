package com.orderflow.catalogo.domain;

import com.orderflow.shared.id.UuidV7Generator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Slot configurável de um produto do tipo {@code COMBO} ({@code combo_slots}).
 */
@Entity
@Table(name = "combo_slots")
public class ComboSlot {

    @Id
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "loja_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID lojaId;

    @Column(name = "combo_produto_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID produtoComboId;

    @Column(name = "nome", nullable = false, length = 60)
    private String nome;

    @Column(name = "min_selecao", nullable = false)
    private int minSelecao = 1;

    @Column(name = "max_selecao", nullable = false)
    private int maxSelecao = 1;

    @Column(name = "ordem", nullable = false)
    private int ordem = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ComboSlot() {
    }

    public ComboSlot(
            UUID id,
            UUID lojaId,
            UUID produtoComboId,
            String nome,
            int minSelecao,
            int maxSelecao,
            int ordem
    ) {
        this.id = id != null ? id : UuidV7Generator.nextId();
        this.lojaId = Objects.requireNonNull(lojaId, "lojaId é obrigatório");
        this.produtoComboId = Objects.requireNonNull(produtoComboId, "produtoComboId é obrigatório");
        this.nome = Objects.requireNonNull(nome, "nome é obrigatório").trim();
        this.minSelecao = minSelecao;
        this.maxSelecao = maxSelecao;
        this.ordem = Math.max(0, ordem);
    }

    @PrePersist
    protected void prePersist() {
        if (this.id == null) {
            this.id = UuidV7Generator.nextId();
        }
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
    }

    public boolean isObrigatorio() {
        return this.minSelecao >= 1;
    }

    public UUID getId() {
        return id;
    }

    public UUID getLojaId() {
        return lojaId;
    }

    public UUID getProdutoComboId() {
        return produtoComboId;
    }

    public String getNome() {
        return nome;
    }

    public int getMinSelecao() {
        return minSelecao;
    }

    public int getMaxSelecao() {
        return maxSelecao;
    }

    public int getOrdem() {
        return ordem;
    }
}
