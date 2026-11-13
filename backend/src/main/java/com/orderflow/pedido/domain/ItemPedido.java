package com.orderflow.pedido.domain;

import com.orderflow.catalogo.domain.EstacaoPreparo;
import com.orderflow.shared.id.UuidV7Generator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Item do Pedido ({@code itens_pedido}) com snapshot de nome e preço unitário no momento da venda.
 */
@Entity
@Table(name = "itens_pedido")
public class ItemPedido {

    @Id
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "loja_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID lojaId;

    @Column(name = "pedido_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID pedidoId;

    @Column(name = "produto_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID produtoId;

    @Column(name = "item_pai_id", updatable = false, columnDefinition = "BINARY(16)")
    private UUID itemPaiId;

    @Column(name = "combo_slot_id", updatable = false, columnDefinition = "BINARY(16)")
    private UUID comboSlotId;

    @Column(name = "combo_slot_nome", updatable = false, length = 60)
    private String comboSlotNome;

    @Column(name = "produto_nome", nullable = false, updatable = false, length = 80)
    private String produtoNome;

    @Column(name = "preco_base_centavos", nullable = false, updatable = false)
    private long precoBaseCentavos;

    @Column(name = "quantidade", nullable = false, updatable = false)
    private int quantidade;

    @Column(name = "preco_unitario_final_centavos", nullable = false, updatable = false)
    private long precoUnitarioFinalCentavos;

    @Column(name = "total_centavos", nullable = false, updatable = false)
    private long totalCentavos;

    @Enumerated(EnumType.STRING)
    @Column(name = "estacao_preparo", nullable = false, updatable = false, length = 15)
    private EstacaoPreparo estacaoPreparo;

    @Column(name = "observacao", updatable = false, length = 200)
    private String observacao;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ItemPedido() {
    }

    public ItemPedido(
            UUID id,
            UUID lojaId,
            UUID pedidoId,
            UUID produtoId,
            UUID itemPaiId,
            UUID comboSlotId,
            String comboSlotNome,
            String produtoNome,
            long precoBaseCentavos,
            int quantidade,
            long precoUnitarioFinalCentavos,
            long totalCentavos,
            EstacaoPreparo estacaoPreparo,
            String observacao
    ) {
        this.id = id != null ? id : UuidV7Generator.nextId();
        this.lojaId = Objects.requireNonNull(lojaId);
        this.pedidoId = Objects.requireNonNull(pedidoId);
        this.produtoId = Objects.requireNonNull(produtoId);
        this.itemPaiId = itemPaiId;
        this.comboSlotId = comboSlotId;
        this.comboSlotNome = comboSlotNome;
        this.produtoNome = Objects.requireNonNull(produtoNome);
        this.precoBaseCentavos = precoBaseCentavos;
        this.quantidade = quantidade;
        this.precoUnitarioFinalCentavos = precoUnitarioFinalCentavos;
        this.totalCentavos = totalCentavos;
        this.estacaoPreparo = estacaoPreparo != null ? estacaoPreparo : EstacaoPreparo.COZINHA;
        this.observacao = observacao != null ? observacao.trim() : null;
        this.createdAt = Instant.now();
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

    public UUID getId() {
        return id;
    }

    public UUID getLojaId() {
        return lojaId;
    }

    public UUID getPedidoId() {
        return pedidoId;
    }

    public UUID getProdutoId() {
        return produtoId;
    }

    public UUID getItemPaiId() {
        return itemPaiId;
    }

    public UUID getComboSlotId() {
        return comboSlotId;
    }

    public String getComboSlotNome() {
        return comboSlotNome;
    }

    public String getProdutoNome() {
        return produtoNome;
    }

    public long getPrecoBaseCentavos() {
        return precoBaseCentavos;
    }

    public int getQuantidade() {
        return quantidade;
    }

    public long getPrecoUnitarioFinalCentavos() {
        return precoUnitarioFinalCentavos;
    }

    public long getTotalCentavos() {
        return totalCentavos;
    }

    public EstacaoPreparo getEstacaoPreparo() {
        return estacaoPreparo;
    }

    public String getObservacao() {
        return observacao;
    }
}
