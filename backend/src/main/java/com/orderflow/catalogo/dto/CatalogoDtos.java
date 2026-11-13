package com.orderflow.catalogo.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.orderflow.catalogo.domain.EstacaoPreparo;
import com.orderflow.catalogo.domain.TipoPreco;
import com.orderflow.catalogo.domain.TipoProduto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Contratos de entrada/saída (DTOs) do Módulo de Catálogo & Cardápio
 * alinhados com {@code openapi.yaml} e com os nomes de domínio solicitados.
 */
public final class CatalogoDtos {

    private CatalogoDtos() {
    }

    // ──────────────────────────── CATEGORIAS ────────────────────────────
    public record CategoriaRequest(
            @JsonAlias({"categoriaPaiId"})
            UUID parentId,

            @NotBlank(message = "Nome da categoria é obrigatório")
            @Size(min = 2, max = 60, message = "Nome da categoria deve ter entre 2 e 60 caracteres")
            String nome,

            @Size(max = 60, message = "Slug deve ter no máximo 60 caracteres")
            String slug,

            @JsonAlias({"ordemExibicao"})
            Integer ordem,

            Boolean ativo,
            EstacaoPreparo estacaoPreparoPadrao,
            Integer versao
    ) {
    }

    public record OrdemItemRequest(
            @NotNull(message = "id é obrigatório")
            UUID id,
            @NotNull(message = "ordem é obrigatória")
            @JsonAlias({"ordemExibicao"})
            Integer ordem
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CategoriaResponse(
            UUID id,
            UUID parentId,
            UUID categoriaPaiId,
            String nome,
            String slug,
            int ordem,
            int ordemExibicao,
            boolean ativo,
            boolean arquivada,
            EstacaoPreparo estacaoPreparoPadrao,
            Instant arquivadoEm,
            Integer versao,
            List<CategoriaResponse> subcategorias,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    // ──────────────────────────── INSUMOS ────────────────────────────
    public record InsumoRequest(
            @NotBlank(message = "Nome do insumo é obrigatório")
            @Size(min = 2, max = 60, message = "Nome do insumo deve ter entre 2 e 60 caracteres")
            String nome,

            @Size(max = 10, message = "Unidade deve ter no máximo 10 caracteres")
            @JsonAlias({"unidadeMedida"})
            String unidade,

            Long quantidadeAtual,
            Long estoqueMinimo,
            Boolean disponivel,
            Integer versao
    ) {
    }

    public record InsumoDisponibilidadeRequest(
            @NotNull(message = "Campo disponivel é obrigatório")
            Boolean disponivel,

            @Size(max = 200, message = "Motivo deve ter no máximo 200 caracteres")
            String motivo
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record InsumoResponse(
            UUID id,
            String nome,
            String unidade,
            String unidadeMedida,
            long quantidadeAtual,
            long estoqueMinimo,
            boolean disponivel,
            String motivoIndisponibilidade,
            Integer versao
    ) {
    }

    public record ItemAfetadoResumo(
            UUID id,
            String nome,
            boolean disponivel
    ) {
    }

    public record InsumoDisponibilidadeResponse(
            InsumoResponse insumo,
            long catalogoVersao,
            List<ItemAfetadoResumo> produtosAfetados,
            List<ItemAfetadoResumo> modificadoresAfetados
    ) {
    }

    // ───────────────────────── MODIFICADORES ─────────────────────────
    public record ModificadorRequest(
            @NotBlank(message = "Nome do modificador é obrigatório")
            @Size(min = 1, max = 60, message = "Nome do modificador deve ter entre 1 e 60 caracteres")
            String nome,

            TipoPreco tipoPreco,

            @JsonAlias({"precoAdicionalCentavos"})
            Long valorPreco,

            Boolean padrao,
            Integer maxQuantidade,
            Integer ordem,
            Boolean ativo,
            Boolean disponivel,
            List<UUID> insumoIds,
            Integer versao
    ) {
    }

    public record AlterarPrecoModificadorRequest(
            @NotNull(message = "versao é obrigatória")
            Integer versao,

            TipoPreco tipoPreco,

            @NotNull(message = "valorPreco é obrigatório")
            @JsonAlias({"precoAdicionalCentavos"})
            Long valorPreco,

            @NotBlank(message = "motivo é obrigatório")
            @Size(min = 5, max = 200, message = "Motivo deve ter entre 5 e 200 caracteres")
            String motivo
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ModificadorResponse(
            UUID id,
            UUID grupoId,
            String nome,
            TipoPreco tipoPreco,
            long valorPreco,
            long precoAdicionalCentavos,
            boolean padrao,
            int maxQuantidade,
            int ordem,
            boolean ativo,
            boolean pausado,
            boolean disponivel,
            String motivoIndisponibilidade,
            List<UUID> insumoIds,
            Integer versao
    ) {
    }

    public record GrupoModificadorRequest(
            @NotBlank(message = "Nome do grupo é obrigatório")
            @Size(min = 2, max = 60, message = "Nome do grupo deve ter entre 2 e 60 caracteres")
            String nome,

            @Size(max = 60)
            String nomeInterno,

            @NotNull(message = "minSelecao é obrigatório")
            Integer minSelecao,

            @NotNull(message = "maxSelecao é obrigatório")
            Integer maxSelecao,

            Boolean permiteRepeticao,
            Integer ordem,
            Boolean ativo,
            Integer versao,

            @Valid
            @JsonAlias({"opcoes"})
            List<ModificadorRequest> modificadores
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record GrupoModificadorResponse(
            UUID id,
            String nome,
            String nomeInterno,
            int minSelecao,
            int maxSelecao,
            boolean permiteRepeticao,
            int ordem,
            boolean ativo,
            Integer versao,
            long produtosVinculados,
            List<ModificadorResponse> modificadores
    ) {
    }

    // ──────────────────────────── PRODUTOS ────────────────────────────
    public record ProdutoRequest(
            @NotNull(message = "categoriaId é obrigatório")
            UUID categoriaId,

            @NotBlank(message = "SKU é obrigatório")
            @Size(min = 2, max = 30, message = "SKU deve ter entre 2 e 30 caracteres")
            String sku,

            @NotBlank(message = "Nome do produto é obrigatório")
            @Size(min = 2, max = 80, message = "Nome do produto deve ter entre 2 e 80 caracteres")
            String nome,

            @Size(max = 500)
            String descricao,

            @Size(max = 500)
            String imagemUrl,

            TipoProduto tipo,
            Long precoBaseCentavos,
            EstacaoPreparo estacaoPreparo,
            Boolean ativo,
            Boolean disponivel,
            Integer ordem,
            Integer versao
    ) {
    }

    public record AlterarPrecoProdutoRequest(
            @NotNull(message = "versao é obrigatória")
            Integer versao,

            @NotNull(message = "precoBaseCentavos é obrigatório")
            Long precoBaseCentavos,

            @NotBlank(message = "motivo é obrigatório")
            @Size(min = 5, max = 200, message = "Motivo deve ter entre 5 e 200 caracteres")
            String motivo
    ) {
    }

    public record DisponibilidadeToggleRequest(
            Boolean pausado,
            Boolean disponivel,
            @Size(max = 200)
            String motivo
    ) {
        public boolean resolvePausado() {
            if (pausado != null) {
                return pausado;
            }
            if (disponivel != null) {
                return !disponivel;
            }
            throw new IllegalArgumentException("Informe 'pausado' ou 'disponivel'");
        }
    }

    public record DisponibilidadeResponse(
            UUID id,
            boolean pausado,
            boolean disponivel,
            String motivoIndisponibilidade,
            long catalogoVersao
    ) {
    }

    public record VinculoGrupoItemRequest(
            @NotNull(message = "grupoId é obrigatório")
            UUID grupoId,
            Integer ordem,
            @JsonAlias({"minSelecaoEfetiva"})
            Integer minSelecaoOverride,
            @JsonAlias({"maxSelecaoEfetiva"})
            Integer maxSelecaoOverride
    ) {
    }

    public record VinculoGruposRequest(
            Integer versao,
            @NotNull(message = "grupos é obrigatório")
            @Valid
            List<VinculoGrupoItemRequest> grupos
    ) {
    }

    public record VinculoGrupoResponse(
            GrupoModificadorResponse grupo,
            int ordem,
            Integer minSelecaoOverride,
            Integer maxSelecaoOverride,
            int minSelecaoEfetiva,
            int maxSelecaoEfetiva
    ) {
    }

    public record VinculoInsumosRequest(
            Integer versao,
            @NotNull(message = "insumoIds é obrigatório")
            List<UUID> insumoIds
    ) {
    }

    // ───────────────────────────── COMBOS ─────────────────────────────
    public record ComboSlotOpcaoRequest(
            @NotNull(message = "produtoId é obrigatório")
            UUID produtoId,
            Long acrescimoCentavos,
            Boolean padrao,
            Integer ordem
    ) {
    }

    public record ComboSlotItemRequest(
            UUID id,

            @NotBlank(message = "Nome do slot é obrigatório")
            @Size(min = 2, max = 60)
            String nome,

            Integer minSelecao,
            Integer maxSelecao,
            Boolean obrigatorio,
            Integer ordem,

            @NotEmpty(message = "Slot do combo deve possuir ao menos uma opção")
            @Valid
            List<ComboSlotOpcaoRequest> opcoes
    ) {
        public int resolveMinSelecao() {
            if (minSelecao != null) {
                return minSelecao;
            }
            return Boolean.FALSE.equals(obrigatorio) ? 0 : 1;
        }

        public int resolveMaxSelecao() {
            if (maxSelecao != null) {
                return maxSelecao;
            }
            return 1;
        }
    }

    public record ComboSlotsRequest(
            Integer versao,
            @NotEmpty(message = "Informe ao menos um slot para o combo")
            @Valid
            List<ComboSlotItemRequest> slots
    ) {
    }

    public record ComboSlotOpcaoResponse(
            UUID produtoId,
            String produtoNome,
            long acrescimoCentavos,
            boolean padrao,
            boolean disponivel
    ) {
    }

    public record ComboSlotResponse(
            UUID id,
            String nome,
            int minSelecao,
            int maxSelecao,
            boolean obrigatorio,
            int ordem,
            List<ComboSlotOpcaoResponse> opcoes
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ProdutoResumoResponse(
            UUID id,
            UUID categoriaId,
            String sku,
            String nome,
            TipoProduto tipo,
            long precoBaseCentavos,
            String imagemUrl,
            boolean ativo,
            boolean pausado,
            boolean disponivel,
            Integer versao
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ProdutoDetalheResponse(
            UUID id,
            UUID categoriaId,
            String sku,
            String nome,
            String descricao,
            String imagemUrl,
            TipoProduto tipo,
            long precoBaseCentavos,
            EstacaoPreparo estacaoPreparo,
            EstacaoPreparo estacaoPreparoEfetiva,
            boolean ativo,
            boolean pausado,
            boolean disponivel,
            String motivoPausa,
            String motivoIndisponibilidade,
            int ordem,
            Integer versao,
            List<VinculoGrupoResponse> grupos,
            List<InsumoResponse> insumos,
            List<ComboSlotResponse> comboSlots,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record HistoricoPrecoResponse(
            UUID id,
            TipoPreco tipoPrecoAnterior,
            Long valorAnterior,
            TipoPreco tipoPrecoNovo,
            long valorNovo,
            String motivo,
            Instant vigenteDesde
    ) {
    }

    // ─────────────────────── PROJEÇÃO DE CATÁLOGO & STOMP ───────────────────────
    public record CatalogoResponse(
            long versao,
            Instant geradoEm,
            List<CatalogoCategoriaNode> categorias
    ) {
    }

    public record CatalogoCategoriaNode(
            UUID id,
            String nome,
            int ordem,
            List<CatalogoProdutoNode> produtos,
            List<CatalogoCategoriaNode> subcategorias
    ) {
    }

    public record CatalogoProdutoNode(
            UUID id,
            String nome,
            String descricao,
            String imagemUrl,
            TipoProduto tipo,
            long precoBaseCentavos,
            boolean disponivel,
            String motivoIndisponibilidade,
            int ordem,
            List<CatalogoGrupoNode> grupos,
            List<ComboSlotResponse> comboSlots
    ) {
    }

    public record CatalogoGrupoNode(
            UUID id,
            String nome,
            int minSelecao,
            int maxSelecao,
            boolean permiteRepeticao,
            List<ModificadorResponse> modificadores
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CatalogoEventoStomp(
            long versao,
            String tipo,
            List<UUID> ids,
            List<DisponibilidadeItemEvento> disponibilidade
    ) {
    }

    public record DisponibilidadeItemEvento(
            UUID id,
            String entidade,
            boolean disponivel
    ) {
    }

    public record PageResponse<T>(
            List<T> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }
}
