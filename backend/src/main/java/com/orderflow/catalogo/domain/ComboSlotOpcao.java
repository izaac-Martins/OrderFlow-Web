package com.orderflow.catalogo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Opção de produto simples permitida dentro de um {@link ComboSlot} ({@code combo_slot_opcoes}).
 */
@Entity
@Table(name = "combo_slot_opcoes")
public class ComboSlotOpcao {

    @EmbeddedId
    private Id id;

    @Column(name = "loja_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID lojaId;

    @Column(name = "acrescimo_centavos", nullable = false)
    private long acrescimoCentavos = 0L;

    @Column(name = "padrao", nullable = false)
    private boolean padrao = false;

    @Column(name = "ordem", nullable = false)
    private int ordem = 0;

    protected ComboSlotOpcao() {
    }

    public ComboSlotOpcao(
            UUID lojaId,
            UUID slotId,
            UUID produtoId,
            long acrescimoCentavos,
            boolean padrao,
            int ordem
    ) {
        this.id = new Id(slotId, produtoId);
        this.lojaId = Objects.requireNonNull(lojaId, "lojaId é obrigatório");
        this.acrescimoCentavos = acrescimoCentavos;
        this.padrao = padrao;
        this.ordem = Math.max(0, ordem);
    }

    public UUID getSlotId() {
        return id.getSlotId();
    }

    public UUID getProdutoId() {
        return id.getProdutoId();
    }

    public UUID getLojaId() {
        return lojaId;
    }

    public long getAcrescimoCentavos() {
        return acrescimoCentavos;
    }

    public boolean isPadrao() {
        return padrao;
    }

    public int getOrdem() {
        return ordem;
    }

    @Embeddable
    public static class Id implements Serializable {

        @Column(name = "slot_id", nullable = false, columnDefinition = "BINARY(16)")
        private UUID slotId;

        @Column(name = "produto_id", nullable = false, columnDefinition = "BINARY(16)")
        private UUID produtoId;

        protected Id() {
        }

        public Id(UUID slotId, UUID produtoId) {
            this.slotId = Objects.requireNonNull(slotId);
            this.produtoId = Objects.requireNonNull(produtoId);
        }

        public UUID getSlotId() {
            return slotId;
        }

        public UUID getProdutoId() {
            return produtoId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Id that)) return false;
            return Objects.equals(slotId, that.slotId) && Objects.equals(produtoId, that.produtoId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(slotId, produtoId);
        }
    }
}
