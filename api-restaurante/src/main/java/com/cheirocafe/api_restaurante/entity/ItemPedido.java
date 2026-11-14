package com.cheirocafe.api_restaurante.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "itens_pedido")
public class ItemPedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = true)
    private Long produtoId;

    @Column(nullable = false)
    private String nomeProduto;

    @Column(length = 100)
    private String tamanhoEscolhido;

    @Column(length = 100)
    private String tipoGraoEscolhido;

    @Column(length = 500)
    private String adicionaisEscolhidos;

    @Column(nullable = false)
    private Integer quantidade;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precoTotalItem;

    @Column(length = 120)
    private String pagadorItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    public ItemPedido() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getProdutoId() { return produtoId; }
    public void setProdutoId(Long produtoId) { this.produtoId = produtoId; }

    public String getNomeProduto() { return nomeProduto; }
    public void setNomeProduto(String nomeProduto) { this.nomeProduto = nomeProduto; }

    public String getTamanhoEscolhido() { return tamanhoEscolhido; }
    public void setTamanhoEscolhido(String tamanhoEscolhido) { this.tamanhoEscolhido = tamanhoEscolhido; }

    public String getTipoGraoEscolhido() { return tipoGraoEscolhido; }
    public void setTipoGraoEscolhido(String tipoGraoEscolhido) { this.tipoGraoEscolhido = tipoGraoEscolhido; }

    public String getAdicionaisEscolhidos() { return adicionaisEscolhidos; }
    public void setAdicionaisEscolhidos(String adicionaisEscolhidos) { this.adicionaisEscolhidos = adicionaisEscolhidos; }

    public Integer getQuantidade() { return quantidade; }
    public void setQuantidade(Integer quantidade) { this.quantidade = quantidade; }

    public BigDecimal getPrecoTotalItem() { return precoTotalItem; }
    public void setPrecoTotalItem(BigDecimal precoTotalItem) {
        this.precoTotalItem = precoTotalItem != null ? precoTotalItem.setScale(2, java.math.RoundingMode.HALF_UP) : null;
    }

    public String getPagadorItem() { return pagadorItem; }
    public void setPagadorItem(String pagadorItem) { this.pagadorItem = pagadorItem; }

    public Pedido getPedido() { return pedido; }
    public void setPedido(Pedido pedido) { this.pedido = pedido; }
}
