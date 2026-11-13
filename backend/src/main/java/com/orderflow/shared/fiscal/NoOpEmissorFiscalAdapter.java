package com.orderflow.shared.fiscal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class NoOpEmissorFiscalAdapter implements EmissorFiscalPort {

    private static final Logger log = LoggerFactory.getLogger(NoOpEmissorFiscalAdapter.class);

    @Override
    public void solicitarEmissaoPedidoQuitado(UUID lojaId, UUID pedidoId) {
        log.debug("[Fiscal No-Op] Ponto de extensão fiscal acionado para lojaId={}, pedidoId={}", lojaId, pedidoId);
    }
}
