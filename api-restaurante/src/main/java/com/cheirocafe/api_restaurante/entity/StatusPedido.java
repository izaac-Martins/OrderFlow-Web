package com.cheirocafe.api_restaurante.entity;

public enum StatusPedido {
    RECEBIDO,
    EM_PREPARO,
    PRONTO,
    ENTREGUE,
    CANCELADO;

    public boolean podeTransicionarPara(StatusPedido novoStatus) {
        if (novoStatus == null || this == novoStatus) {
            return false;
        }
        return switch (this) {
            case RECEBIDO -> novoStatus == EM_PREPARO || novoStatus == CANCELADO;
            case EM_PREPARO -> novoStatus == PRONTO || novoStatus == CANCELADO;
            case PRONTO -> novoStatus == ENTREGUE || novoStatus == CANCELADO;
            case ENTREGUE, CANCELADO -> false;
        };
    }

    public boolean isEstadoTerminal() {
        return this == ENTREGUE || this == CANCELADO;
    }
}
