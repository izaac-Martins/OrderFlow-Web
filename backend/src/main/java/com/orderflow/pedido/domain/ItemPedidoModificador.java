package com.orderflow.pedido.domain;

import com.orderflow.catalogo.domain.TipoPreco;
import com.orderflow.shared.id.UuidV7Generator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

/**
 * Modificador aplicado a um {@link ItemPedido} ({@code itens_pedido_modificadores}) com snapshot completo.
 */
@Entity
@Table(name = "itens_pedido_modificadores")
public class ItemPedidoModificador {

    @Id
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "loja_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID lojaId;

    @Column(name = "item_pedido_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID itemPedidoId;

    @Column(name = "modificador_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID modificadorId;

    @Column(name = "grupo_nome", nullable = false, updatable = false, length = 60)
    private String grupoNome;

    @Column(name = "modificador_nome", nullable = false, updatable = false, length = 60)
    private String modificadorNome;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_preco", nullable = false, updatable = false, length = 15)
    private TipoPreco tipoPreco;

    @Column(name = "valor_preco", nullable = false, updatable = false)
    private long valorPreco;

    @Column(name = "quantidade", nullable = false, updatable = false)
    private int quantidade;

    @Column(name = "valor_calculado_centavos", nullable = false, updatable = false)
    private long valorCalculadoCentavos;

    protected ItemPedidoModificador() {
    }

    public ItemPedidoModificador(
            UUID lojaId,
            UUID itemPedidoId,
            UUID modificadorId,
            String grupoNome,
            String modificadorNome,
            TipoPreco tipoPreco,
            long valorPreco,
            int quantidade,
            long valorCalculadoCentavos
    ) {
        this.id = UuidV7Generator.nextId();
        this.lojaId = Objects.requireNonNull(lojaId);
        this.itemPedidoId = Objects.requireNonNull(itemPedidoId);
        this.modificadorId = Objects.requireNonNull(modificadorId);
        this.grupoNome = Objects.requireNonNull(grupoNome);
        this.modificadorNome = Objects.requireNonNull(modificadorNome);
        this.tipoPreco = Objects.requireNonNull(tipoPreco);
        this.valorPreco = valorPreco;
        this.quantidade = quantidade;
        this.valorCalculadoCentavos = valorCalculadoCentavos;
    }

    @PrePersist
    protected void prePersist() {
        if (this.id == null) {
            this.id = UuidV7Generator.nextId();
        }
    }

    public String formatarRotuloKds() {
        return this.quantidade > 1 ? this.quantidade + "x " + this.modificadorNome : this.modificadorNome;
    }

    public UUID getId() {
        return id;
    }

    public UUID getLojaId() {
        return lojaId;
    }

    public UUID getItemPedidoId() {
        return itemPedidoId;
    }

    public UUID getModificadorId() {
        return modificadorId;
    }

    public String getGrupoNome() {
        return grupoNome;
    }

    public String getModificadorNome() {
        return modificadorNome;
    }

    public TipoPreco getTipoPreco() {
        return tipoPreco;
    }

    public long getValorPreco() {
        return valorPreco;
    }

    public int getQuantidade() {
        return quantidade;
    }

    public long getValorCalculadoCentavos() {
        return valorCalculadoCentavos;
    }
}
