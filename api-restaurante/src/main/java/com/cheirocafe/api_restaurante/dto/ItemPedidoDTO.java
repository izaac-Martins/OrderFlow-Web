package com.cheirocafe.api_restaurante.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class ItemPedidoDTO {

    private Long id;
    private Long produtoId;

    @NotBlank(message = "O nome do produto no item do pedido é obrigatório")
    private String nomeProduto;

    private String tamanhoEscolhido;
    private String tipoGraoEscolhido;
    private String adicionaisEscolhidos;

    @NotNull(message = "A quantidade do item é obrigatória")
    @Positive(message = "A quantidade do item deve ser maior que zero")
    private Integer quantidade;

    @NotNull(message = "O preço total do item é obrigatório")
    @PositiveOrZero(message = "O preço total do item não pode ser negativo")
    @JsonAlias({"precoUnitario", "precoTotalItem"})
    private BigDecimal precoTotalItem;

    private String pagadorItem;

    public ItemPedidoDTO() {}

    public ItemPedidoDTO(
            Long id,
            Long produtoId,
            String nomeProduto,
            String tamanhoEscolhido,
            String tipoGraoEscolhido,
            String adicionaisEscolhidos,
            Integer quantidade,
            BigDecimal precoTotalItem,
            String pagadorItem
    ) {
        this.id = id;
        this.produtoId = produtoId;
        this.nomeProduto = nomeProduto;
        this.tamanhoEscolhido = tamanhoEscolhido;
        this.tipoGraoEscolhido = tipoGraoEscolhido;
        this.adicionaisEscolhidos = adicionaisEscolhidos;
        this.quantidade = quantidade;
        this.precoTotalItem = precoTotalItem;
        this.pagadorItem = pagadorItem;
    }

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
    public void setPrecoTotalItem(BigDecimal precoTotalItem) { this.precoTotalItem = precoTotalItem; }

    public BigDecimal getPrecoUnitario() {
        if (precoTotalItem == null) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        int qtd = (quantidade != null && quantidade > 0) ? quantidade : 1;
        return precoTotalItem.divide(BigDecimal.valueOf(qtd), 2, RoundingMode.HALF_UP);
    }

    public String getPagadorItem() { return pagadorItem; }
    public void setPagadorItem(String pagadorItem) { this.pagadorItem = pagadorItem; }
}
