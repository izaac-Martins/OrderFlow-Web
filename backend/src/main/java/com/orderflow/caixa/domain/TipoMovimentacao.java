package com.orderflow.caixa.domain;

public enum TipoMovimentacao {
    ABERTURA(NaturezaMovimentacao.ENTRADA),
    SUPRIMENTO(NaturezaMovimentacao.ENTRADA),
    VENDA(NaturezaMovimentacao.ENTRADA),
    VENDA_DINHEIRO(NaturezaMovimentacao.ENTRADA),
    SANGRIA(NaturezaMovimentacao.SAIDA),
    ESTORNO(NaturezaMovimentacao.SAIDA),
    ESTORNO_VENDA_DINHEIRO(NaturezaMovimentacao.SAIDA),
    ESTORNO_MOVIMENTACAO(NaturezaMovimentacao.SAIDA);

    private final NaturezaMovimentacao naturezaPadrao;

    TipoMovimentacao(NaturezaMovimentacao naturezaPadrao) {
        this.naturezaPadrao = naturezaPadrao;
    }

    public NaturezaMovimentacao getNaturezaPadrao() {
        return naturezaPadrao;
    }

    public boolean isEntradaDinheiro() {
        return naturezaPadrao == NaturezaMovimentacao.ENTRADA;
    }
}
