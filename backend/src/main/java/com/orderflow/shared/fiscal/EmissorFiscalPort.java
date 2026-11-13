package com.orderflow.shared.fiscal;

import java.util.UUID;

/**
 * Porta de saída (Hexagonal / Clean Architecture) para emissão fiscal futura (NFC-e / SAT)
 * conforme Decisão D4. Nesta fase opera com adaptador no-op desacoplado do domínio de pagamentos.
 */
public interface EmissorFiscalPort {

    void solicitarEmissaoPedidoQuitado(UUID lojaId, UUID pedidoId);
}
