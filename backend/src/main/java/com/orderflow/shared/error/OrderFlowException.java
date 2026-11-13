package com.orderflow.shared.error;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Exceção base de domínio/aplicação mapeada diretamente para um {@link CodigoErro}
 * e serializada pelo {@link GlobalExceptionHandler} como RFC 7807 {@link ProblemResponse}.
 */
public class OrderFlowException extends RuntimeException {

    private final CodigoErro codigoErro;
    private final List<ProblemResponse.CampoErro> erros;
    private final ProblemResponse.EstadoAtualRecurso estadoAtual;
    private final List<UUID> itensIndisponiveis;

    public OrderFlowException(CodigoErro codigoErro, String detail) {
        this(codigoErro, detail, Collections.emptyList(), null, Collections.emptyList());
    }

    public OrderFlowException(
            CodigoErro codigoErro,
            String detail,
            List<ProblemResponse.CampoErro> erros,
            ProblemResponse.EstadoAtualRecurso estadoAtual,
            List<UUID> itensIndisponiveis
    ) {
        super(detail != null ? detail : codigoErro.getDefaultTitle());
        this.codigoErro = Objects.requireNonNull(codigoErro, "codigoErro é obrigatório");
        this.erros = erros != null ? List.copyOf(erros) : Collections.emptyList();
        this.estadoAtual = estadoAtual;
        this.itensIndisponiveis = itensIndisponiveis != null ? List.copyOf(itensIndisponiveis) : Collections.emptyList();
    }

    public static OrderFlowException conflitoEstado(CodigoErro codigoErro, String detail, String statusAtual, Integer versaoAtual) {
        return new OrderFlowException(
                codigoErro,
                detail,
                Collections.emptyList(),
                new ProblemResponse.EstadoAtualRecurso(statusAtual, versaoAtual),
                Collections.emptyList()
        );
    }

    public static OrderFlowException itensIndisponiveis(String detail, List<UUID> idsIndisponiveis) {
        return new OrderFlowException(
                CodigoErro.ITEM_INDISPONIVEL,
                detail,
                Collections.emptyList(),
                null,
                idsIndisponiveis
        );
    }

    public CodigoErro getCodigoErro() {
        return codigoErro;
    }

    public List<ProblemResponse.CampoErro> getErros() {
        return erros;
    }

    public ProblemResponse.EstadoAtualRecurso getEstadoAtual() {
        return estadoAtual;
    }

    public List<UUID> getItensIndisponiveis() {
        return itensIndisponiveis;
    }
}
