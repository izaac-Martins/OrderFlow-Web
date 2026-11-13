package com.orderflow.pedido.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.orderflow.caixa.dto.CaixaDtos.PagamentoInicialRequest;
import com.orderflow.caixa.dto.CaixaDtos.PagamentoResponse;
import com.orderflow.catalogo.domain.EstacaoPreparo;
import com.orderflow.catalogo.domain.TipoPreco;
import com.orderflow.pedido.domain.ModalidadePagamento;
import com.orderflow.pedido.domain.OrigemPedido;
import com.orderflow.pedido.domain.StatusPedido;
import com.orderflow.pedido.domain.TipoAtendimento;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Contratos (DTOs) do Módulo de Pedidos e KDS (Cozinha em Tempo Real).
 */
public final class PedidoDtos {

    private PedidoDtos() {
    }

    public record ModificadorSelecionadoRequest(
            @NotNull(message = "modificadorId é obrigatório")
            UUID modificadorId,

            @Min(value = 1, message = "quantidade deve ser pelo menos 1")
            Integer quantidade
    ) {
        public int resolveQuantidade() {
            return quantidade != null ? quantidade : 1;
        }
    }

    public record SelecaoComboSlotRequest(
            @NotNull(message = "slotId é obrigatório")
            UUID slotId,

            @NotNull(message = "produtoId é obrigatório")
            UUID produtoId,

            @Size(max = 200)
            String observacao,

            @Valid
            List<ModificadorSelecionadoRequest> modificadores
    ) {
    }

    public record ItemPedidoRequest(
            @NotNull(message = "produtoId é obrigatório")
            UUID produtoId,

            @NotNull(message = "quantidade é obrigatória")
            @Min(value = 1, message = "quantidade deve ser pelo menos 1")
            @Max(value = 99, message = "quantidade máxima por item é 99")
            Integer quantidade,

            @Size(max = 200)
            String observacao,

            @Valid
            List<ModificadorSelecionadoRequest> modificadores,

            @Valid
            List<SelecaoComboSlotRequest> selecoesCombo
    ) {
    }

    public record CriarPedidoRequest(
            OrigemPedido origem,
            TipoAtendimento tipoAtendimento,
            ModalidadePagamento modalidadePagamento,
            UUID sessaoCaixaId,
            UUID clienteId,

            @Size(max = 60)
            String identificadorCliente,

            @Size(max = 10)
            String mesa,

            @Size(max = 300)
            String observacao,

            Long descontoCentavos,
            Long catalogoVersao,
            Long totalEsperadoCentavos,

            @Valid
            PagamentoInicialRequest pagamentoInicial,

            @NotEmpty(message = "O pedido deve conter pelo menos um item")
            @Valid
            List<ItemPedidoRequest> itens
    ) {
    }

    public record VersaoRequest(
            @NotNull(message = "versao é obrigatória")
            Integer versao
    ) {
    }

    public record CancelarPedidoRequest(
            @NotNull(message = "versao é obrigatória")
            Integer versao,

            @NotBlank(message = "motivo é obrigatório")
            @Size(min = 5, max = 200, message = "Motivo deve ter entre 5 e 200 caracteres")
            String motivo,

            Boolean registrarPerda,
            UUID sessaoCaixaId,

            @JsonAlias({"pin", "pinAlcada"})
            String pinAprovador
    ) {
    }

    public record RefazerPedidoRequest(
            @NotNull(message = "versao é obrigatória")
            Integer versao,

            @NotBlank(message = "motivo é obrigatório")
            @Size(min = 5, max = 200)
            String motivo
    ) {
    }

    public record AlterarStatusKdsRequest(
            @NotNull(message = "versao é obrigatória")
            Integer versao,

            @NotNull(message = "status é obrigatório")
            StatusPedido status,

            String motivo
    ) {
    }

    public record ItemPedidoModificadorResponse(
            UUID modificadorId,
            String grupoNome,
            String modificadorNome,
            TipoPreco tipoPreco,
            long valorPreco,
            int quantidade,
            long valorCalculadoCentavos
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ItemPedidoResponse(
            UUID id,
            UUID produtoId,
            String produtoNome,
            int quantidade,
            long precoBaseCentavos,
            long precoUnitarioFinalCentavos,
            long totalCentavos,
            EstacaoPreparo estacaoPreparo,
            String observacao,
            String comboSlotNome,
            List<ItemPedidoModificadorResponse> modificadores,
            List<ItemPedidoResponse> filhos
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PedidoResponse(
            UUID id,
            UUID sessaoCaixaId,
            UUID clienteId,
            int numeroDiario,
            LocalDate dataOperacional,
            OrigemPedido origem,
            TipoAtendimento tipoAtendimento,
            ModalidadePagamento modalidadePagamento,
            String identificadorCliente,
            String mesa,
            String observacao,
            StatusPedido status,
            Integer versao,
            List<ItemPedidoResponse> itens,
            long subtotalCentavos,
            long descontoCentavos,
            long totalCentavos,
            long valorTotalCentavos,
            long totalPagoCentavos,
            long saldoDevedorCentavos,
            List<PagamentoResponse> pagamentos,
            String motivoCancelamento,
            String fiscalStatus,
            Instant confirmadoEm,
            Instant preparoIniciadoEm,
            Instant prontoEm,
            Instant entregueEm,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    // ─────────────────────────── VISÃO KDS (SEM FINANÇAS) ───────────────────────────
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ItemCozinhaDto(
            UUID itemId,
            String produtoNome,
            int quantidade,
            EstacaoPreparo estacao,
            List<String> modificadores,
            String observacao,
            String comboNome
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PedidoCozinhaDto(
            UUID pedidoId,
            int numeroDiario,
            String identificadorCliente,
            String mesa,
            TipoAtendimento tipoAtendimento,
            StatusPedido status,
            int versao,
            String observacao,
            Instant confirmadoEm,
            Instant preparoIniciadoEm,
            Instant prontoEm,
            List<ItemCozinhaDto> itens
    ) {
    }

    public record CozinhaSnapshotResponse(
            long seqAtual,
            Instant geradoEm,
            List<PedidoCozinhaDto> pedidos
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PedidoCozinhaEventoDto(
            long seq,
            String tipo,
            UUID pedidoId,
            int versao,
            StatusPedido status,
            Instant ocorridoEm,
            String motivo,
            PedidoCozinhaDto pedido
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PedidoProntoEventoDto(
            UUID pedidoId,
            int numeroDiario,
            String identificadorCliente,
            String mesa
    ) {
    }
}
