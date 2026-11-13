package com.orderflow.shared.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.UUID;

/**
 * Envelope RFC 7807 ({@code application/problem+json}) estrito conforme especificado em
 * {@code contratos/openapi.yaml} (schema {@code Problem}) e {@code contratos/catalogo_erros.md}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProblemResponse(
        String type,
        String title,
        int status,
        CodigoErro code,
        String detail,
        String instance,
        String traceId,
        List<CampoErro> erros,
        EstadoAtualRecurso estadoAtual,
        List<UUID> itensIndisponiveis
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CampoErro(
            String campo,
            String mensagem,
            String codigo
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EstadoAtualRecurso(
            String status,
            Integer versao
    ) {
    }
}
