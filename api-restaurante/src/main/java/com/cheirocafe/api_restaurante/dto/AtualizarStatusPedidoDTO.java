package com.cheirocafe.api_restaurante.dto;

import com.cheirocafe.api_restaurante.entity.StatusPedido;
import jakarta.validation.constraints.NotNull;

public class AtualizarStatusPedidoDTO {

    @NotNull(message = "O novo status do pedido é obrigatório")
    private StatusPedido status;

    public AtualizarStatusPedidoDTO() {}

    public AtualizarStatusPedidoDTO(StatusPedido status) {
        this.status = status;
    }

    public StatusPedido getStatus() {
        return status;
    }

    public void setStatus(StatusPedido status) {
        this.status = status;
    }
}
