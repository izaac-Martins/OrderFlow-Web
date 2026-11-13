package com.orderflow.caixa.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.orderflow.caixa.domain.MetodoPagamento;
import com.orderflow.caixa.domain.NaturezaMovimentacao;
import com.orderflow.caixa.domain.SituacaoConferencia;
import com.orderflow.caixa.domain.StatusPagamento;
import com.orderflow.caixa.domain.StatusSessaoCaixa;
import com.orderflow.caixa.domain.TipoMovimentacao;
import com.orderflow.pedido.domain.StatusPedido;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Contratos (DTOs) do Módulo de Caixa Rigoroso e Pagamentos.
 */
public final class CaixaDtos {

    private CaixaDtos() {
    }

    public record UsuarioRefDto(
            UUID id,
            String nome
    ) {
    }

    public record CriarTerminalCaixaRequest(
            @NotBlank(message = "Nome do terminal é obrigatório")
            @Size(min = 2, max = 60)
            String nome,
            String impressoraIp,
            Integer impressoraPorta,
            Boolean possuiGaveta
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CaixaResponse(
            UUID id,
            String nome,
            boolean possuiGaveta,
            boolean ativo,
            UUID sessaoAbertaId,
            UsuarioRefDto operadorAtual
    ) {
    }

    public record AbrirSessaoRequest(
            @NotNull(message = "caixaId é obrigatório")
            UUID caixaId,

            @NotNull(message = "fundoTrocoCentavos é obrigatório")
            @Min(value = 0, message = "fundoTrocoCentavos não pode ser negativo")
            @JsonAlias({"saldoInicialCentavos"})
            Long fundoTrocoCentavos
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SessaoCaixaOperadorResponse(
            UUID id,
            UUID caixaId,
            String caixaNome,
            UsuarioRefDto operador,
            StatusSessaoCaixa status,
            Instant abertaEm,
            Instant dataAbertura,
            long fundoTrocoCentavos,
            long quantidadePagamentos,
            long pedidosPosPagoEmAberto,
            Integer versao
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SessaoCaixaGerencialResponse(
            UUID id,
            UUID caixaId,
            String caixaNome,
            UsuarioRefDto operador,
            StatusSessaoCaixa status,
            Instant abertaEm,
            Instant fechadaEm,
            Instant conferidaEm,
            UsuarioRefDto conferidaPor,
            long fundoTrocoCentavos,
            Long esperadoDinheiroCentavos,
            Long declaradoDinheiroCentavos,
            Long diferencaDinheiroCentavos,
            boolean possuiSinalizacoes,
            Integer versao
    ) {
    }

    public record MovimentacaoCaixaRequest(
            UUID sessaoCaixaId,

            TipoMovimentacao tipo,

            @NotNull(message = "valorCentavos é obrigatório")
            @Min(value = 1, message = "valorCentavos deve ser maior que zero")
            Long valorCentavos,

            @NotBlank(message = "motivo é obrigatório")
            @Size(min = 5, max = 200, message = "Motivo deve ter entre 5 e 200 caracteres")
            String motivo,

            @JsonAlias({"pin", "pinAlcada"})
            String pinAprovador
    ) {
    }

    public record EstornoRequest(
            @NotBlank(message = "motivo é obrigatório")
            @Size(min = 5, max = 200, message = "Motivo deve ter entre 5 e 200 caracteres")
            String motivo
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MovimentacaoOperadorResponse(
            UUID id,
            UUID sessaoCaixaId,
            TipoMovimentacao tipo,
            NaturezaMovimentacao natureza,
            long valorCentavos,
            String motivo,
            UUID usuarioAprovadorId,
            UsuarioRefDto aprovadoPor,
            Instant createdAt,
            UsuarioRefDto createdBy
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MovimentacaoGerencialResponse(
            UUID id,
            UUID sessaoCaixaId,
            TipoMovimentacao tipo,
            NaturezaMovimentacao natureza,
            long valorCentavos,
            String motivo,
            UUID pagamentoId,
            UUID movimentacaoEstornadaId,
            UUID usuarioAprovadorId,
            UsuarioRefDto aprovadoPor,
            String sinalizacao,
            Instant createdAt,
            UsuarioRefDto createdBy
    ) {
    }

    public record DeclaracaoMetodoRequest(
            @NotNull(message = "metodo é obrigatório")
            MetodoPagamento metodo,

            @NotNull(message = "valorDeclaradoCentavos é obrigatório")
            @Min(value = 0, message = "valorDeclaradoCentavos não pode ser negativo")
            Long valorDeclaradoCentavos,

            Integer quantidadeComprovantes
    ) {
    }

    public record FechamentoCegoRequest(
            UUID sessaoCaixaId,
            Integer versao,

            @Size(max = 300)
            String observacao,

            @NotEmpty(message = "Informe ao menos a declaração de DINHEIRO")
            @Valid
            List<DeclaracaoMetodoRequest> declaracoes
    ) {
    }

    public record FechamentoProtocoloResponse(
            UUID sessaoId,
            String protocolo,
            StatusSessaoCaixa status,
            Instant fechadaEm,
            List<String> alertas
    ) {
    }

    public record ConferenciaMetodoItem(
            MetodoPagamento metodo,
            long esperadoCentavos,
            long declaradoCentavos,
            long diferencaCentavos,
            SituacaoConferencia situacao,
            Integer quantidadeComprovantesDeclarada,
            long quantidadePagamentos
    ) {
    }

    public record ComposicaoDinheiroDto(
            long aberturaCentavos,
            long vendasCentavos,
            long suprimentosCentavos,
            long sangriasCentavos,
            long estornosCentavos,
            long esperadoCentavos
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ConferenciaResponse(
            UUID sessaoId,
            StatusSessaoCaixa status,
            UsuarioRefDto operador,
            Instant abertaEm,
            Instant fechadaEm,
            List<ConferenciaMetodoItem> porMetodo,
            ComposicaoDinheiroDto composicaoDinheiro,
            List<MovimentacaoGerencialResponse> sinalizacoes,
            String observacaoOperador,
            String observacaoConferencia
    ) {
    }

    public record ConferirSessaoRequest(
            Integer versao,
            @Size(max = 500)
            String observacao
    ) {
    }

    public record TotalPorMetodoDto(
            MetodoPagamento metodo,
            long totalCentavos
    ) {
    }

    public record SaldoSessaoResponse(
            UUID sessaoId,
            long saldoDinheiroEsperadoCentavos,
            List<TotalPorMetodoDto> totaisPorMetodo,
            Instant calculadoEm
    ) {
    }

    // ──────────────────────────── PAGAMENTOS ────────────────────────────
    public record PagamentoInicialRequest(
            UUID sessaoCaixaId,

            @NotNull(message = "metodo é obrigatório")
            MetodoPagamento metodo,

            @NotNull(message = "valorCentavos é obrigatório")
            @Min(value = 1, message = "valorCentavos deve ser maior que zero")
            Long valorCentavos,

            Long valorRecebidoCentavos,
            String nsu,
            String autorizacao,
            String txidPix
    ) {
    }

    public record PagamentoRequest(
            @NotNull(message = "pedidoId é obrigatório")
            UUID pedidoId,

            @NotNull(message = "sessaoCaixaId é obrigatório")
            UUID sessaoCaixaId,

            @NotNull(message = "metodo é obrigatório")
            MetodoPagamento metodo,

            @NotNull(message = "valorCentavos é obrigatório")
            @Min(value = 1, message = "valorCentavos deve ser maior que zero")
            Long valorCentavos,

            Long valorRecebidoCentavos,
            String nsu,
            String autorizacao,
            String txidPix
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PagamentoResponse(
            UUID id,
            UUID pedidoId,
            UUID sessaoCaixaId,
            MetodoPagamento metodo,
            long valorCentavos,
            Long valorRecebidoCentavos,
            Long trocoCentavos,
            StatusPagamento status,
            String nsu,
            String autorizacao,
            String txidPix,
            StatusPedido pedidoStatusApos,
            long saldoDevedorAposCentavos,
            String motivoEstorno,
            Instant estornadoEm,
            Instant createdAt
    ) {
    }
}
