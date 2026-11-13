package com.orderflow.pedido.domain;

import java.util.Set;

/**
 * Máquina de Estados explícita do Pedido no Ecossistema OrderFlow.
 */
public enum StatusPedido {
    CRIADO,
    AGUARDANDO_PAGAMENTO,
    CONFIRMADO,
    EM_PREPARO,
    PRONTO,
    ENTREGUE,
    CANCELADO,
    ESTORNADO;

    private static final Set<StatusPedido> STATUS_ATIVOS_KDS = Set.of(CONFIRMADO, EM_PREPARO, PRONTO);

    public boolean isAtivoNoKds() {
        return STATUS_ATIVOS_KDS.contains(this);
    }

    public boolean isInicialParaCancelamentoPorAtendente() {
        return this == CRIADO || this == AGUARDANDO_PAGAMENTO || this == CONFIRMADO;
    }

    public boolean isTerminal() {
        return this == ENTREGUE || this == CANCELADO || this == ESTORNADO;
    }
}
