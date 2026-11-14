package com.cheirocafe.api_restaurante.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class PedidoRequestDTO {

    @NotNull(message = "O número da mesa é obrigatório")
    @Positive(message = "O número da mesa deve ser maior que zero")
    private Integer numeroMesa;

    @PositiveOrZero(message = "O valor total do pedido não pode ser negativo")
    private BigDecimal valorTotal;

    @NotEmpty(message = "O pedido deve conter ao menos um item")
    @Valid
    private List<ItemPedidoDTO> itens = new ArrayList<>();

    public PedidoRequestDTO() {}

    public Integer getNumeroMesa() { return numeroMesa; }
    public void setNumeroMesa(Integer numeroMesa) { this.numeroMesa = numeroMesa; }

    public BigDecimal getValorTotal() { return valorTotal; }
    public void setValorTotal(BigDecimal valorTotal) { this.valorTotal = valorTotal; }

    public List<ItemPedidoDTO> getItens() { return itens; }
    public void setItens(List<ItemPedidoDTO> itens) {
        this.itens = itens != null ? itens : new ArrayList<>();
    }
}
