package com.orderflow.catalogo.web;

import com.orderflow.catalogo.domain.TipoProduto;
import com.orderflow.catalogo.dto.CatalogoDtos.AlterarPrecoModificadorRequest;
import com.orderflow.catalogo.dto.CatalogoDtos.AlterarPrecoProdutoRequest;
import com.orderflow.catalogo.dto.CatalogoDtos.CatalogoResponse;
import com.orderflow.catalogo.dto.CatalogoDtos.CategoriaRequest;
import com.orderflow.catalogo.dto.CatalogoDtos.CategoriaResponse;
import com.orderflow.catalogo.dto.CatalogoDtos.ComboSlotsRequest;
import com.orderflow.catalogo.dto.CatalogoDtos.DisponibilidadeResponse;
import com.orderflow.catalogo.dto.CatalogoDtos.DisponibilidadeToggleRequest;
import com.orderflow.catalogo.dto.CatalogoDtos.GrupoModificadorRequest;
import com.orderflow.catalogo.dto.CatalogoDtos.GrupoModificadorResponse;
import com.orderflow.catalogo.dto.CatalogoDtos.HistoricoPrecoResponse;
import com.orderflow.catalogo.dto.CatalogoDtos.InsumoDisponibilidadeRequest;
import com.orderflow.catalogo.dto.CatalogoDtos.InsumoDisponibilidadeResponse;
import com.orderflow.catalogo.dto.CatalogoDtos.InsumoRequest;
import com.orderflow.catalogo.dto.CatalogoDtos.InsumoResponse;
import com.orderflow.catalogo.dto.CatalogoDtos.ModificadorRequest;
import com.orderflow.catalogo.dto.CatalogoDtos.ModificadorResponse;
import com.orderflow.catalogo.dto.CatalogoDtos.OrdemItemRequest;
import com.orderflow.catalogo.dto.CatalogoDtos.PageResponse;
import com.orderflow.catalogo.dto.CatalogoDtos.ProdutoDetalheResponse;
import com.orderflow.catalogo.dto.CatalogoDtos.ProdutoRequest;
import com.orderflow.catalogo.dto.CatalogoDtos.ProdutoResumoResponse;
import com.orderflow.catalogo.dto.CatalogoDtos.VinculoGruposRequest;
import com.orderflow.catalogo.dto.CatalogoDtos.VinculoInsumosRequest;
import com.orderflow.catalogo.service.CatalogoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Controller REST do Módulo de Catálogo & Cardápio.
 * Expõe tanto as rotas raiz do {@code openapi.yaml} ({@code /api/v1/categorias}, {@code /api/v1/produtos}, etc.)
 * quanto os aliases sob {@code /api/v1/catalogo/...}.
 */
@RestController
@RequestMapping("/api/v1")
@Validated
public class CatalogoController {

    private final CatalogoService catalogoService;

    public CatalogoController(CatalogoService catalogoService) {
        this.catalogoService = catalogoService;
    }

    // ─────────────────────── PROJEÇÃO DE CATÁLOGO (ETag) ───────────────────────
    @GetMapping("/catalogo")
    public ResponseEntity<CatalogoResponse> obterCatalogo(
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch
    ) {
        long versaoAtual = catalogoService.obterVersaoAtualCatalogo();
        String etag = "\"" + versaoAtual + "\"";
        if (ifNoneMatch != null && (ifNoneMatch.equals(etag) || ifNoneMatch.equals(String.valueOf(versaoAtual)))) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .header(HttpHeaders.ETAG, etag)
                    .build();
        }
        CatalogoResponse projecao = catalogoService.obterCatalogoProjecao();
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, "\"" + projecao.versao() + "\"")
                .body(projecao);
    }

    // ─────────────────────────────── CATEGORIAS ───────────────────────────────
    @GetMapping({"/categorias", "/catalogo/categorias"})
    public ResponseEntity<List<CategoriaResponse>> listarCategorias(
            @RequestParam(defaultValue = "false") boolean incluirArquivadas
    ) {
        return ResponseEntity.ok(catalogoService.listarCategorias(incluirArquivadas));
    }

    @PostMapping({"/categorias", "/catalogo/categorias"})
    public ResponseEntity<CategoriaResponse> criarCategoria(@Valid @RequestBody CategoriaRequest request) {
        CategoriaResponse criada = catalogoService.criarCategoria(request);
        return ResponseEntity.created(URI.create("/api/v1/categorias/" + criada.id())).body(criada);
    }

    @PatchMapping({"/categorias/ordem", "/catalogo/categorias/ordem"})
    public ResponseEntity<Void> reordenarCategorias(
            @RequestBody @NotEmpty @Valid List<OrdemItemRequest> itens
    ) {
        catalogoService.reordenarCategorias(itens);
        return ResponseEntity.noContent().build();
    }

    @GetMapping({"/categorias/{id}", "/catalogo/categorias/{id}"})
    public ResponseEntity<CategoriaResponse> obterCategoria(@PathVariable UUID id) {
        return ResponseEntity.ok(catalogoService.obterCategoria(id));
    }

    @PutMapping({"/categorias/{id}", "/catalogo/categorias/{id}"})
    public ResponseEntity<CategoriaResponse> atualizarCategoria(
            @PathVariable UUID id,
            @Valid @RequestBody CategoriaRequest request
    ) {
        return ResponseEntity.ok(catalogoService.atualizarCategoria(id, request));
    }

    @DeleteMapping({"/categorias/{id}", "/catalogo/categorias/{id}"})
    public ResponseEntity<Void> arquivarCategoria(@PathVariable UUID id) {
        catalogoService.arquivarCategoria(id);
        return ResponseEntity.noContent().build();
    }

    // ──────────────────────────────── INSUMOS ────────────────────────────────
    @GetMapping({"/insumos", "/catalogo/insumos"})
    public ResponseEntity<List<InsumoResponse>> listarInsumos(
            @RequestParam(required = false) Boolean disponivel
    ) {
        return ResponseEntity.ok(catalogoService.listarInsumos(disponivel));
    }

    @PostMapping({"/insumos", "/catalogo/insumos"})
    public ResponseEntity<InsumoResponse> criarInsumo(@Valid @RequestBody InsumoRequest request) {
        InsumoResponse criado = catalogoService.criarInsumo(request);
        return ResponseEntity.created(URI.create("/api/v1/insumos/" + criado.id())).body(criado);
    }

    @PutMapping({"/insumos/{id}", "/catalogo/insumos/{id}"})
    public ResponseEntity<InsumoResponse> atualizarInsumo(
            @PathVariable UUID id,
            @Valid @RequestBody InsumoRequest request
    ) {
        return ResponseEntity.ok(catalogoService.atualizarInsumo(id, request));
    }

    @PatchMapping({"/insumos/{id}/disponibilidade", "/catalogo/insumos/{id}/disponibilidade"})
    public ResponseEntity<InsumoDisponibilidadeResponse> alterarDisponibilidadeInsumo(
            @PathVariable UUID id,
            @Valid @RequestBody InsumoDisponibilidadeRequest request
    ) {
        return ResponseEntity.ok(catalogoService.alterarDisponibilidadeInsumo(id, request));
    }

    // ────────────────────────── GRUPOS E MODIFICADORES ──────────────────────────
    @GetMapping({"/grupos-modificadores", "/catalogo/grupos-modificadores"})
    public ResponseEntity<List<GrupoModificadorResponse>> listarGruposModificadores() {
        return ResponseEntity.ok(catalogoService.listarGruposModificadores());
    }

    @PostMapping({"/grupos-modificadores", "/catalogo/grupos-modificadores"})
    public ResponseEntity<GrupoModificadorResponse> criarGrupoModificador(
            @Valid @RequestBody GrupoModificadorRequest request
    ) {
        GrupoModificadorResponse criado = catalogoService.criarGrupoModificador(request);
        return ResponseEntity.created(URI.create("/api/v1/grupos-modificadores/" + criado.id())).body(criado);
    }

    @GetMapping({"/grupos-modificadores/{id}", "/catalogo/grupos-modificadores/{id}"})
    public ResponseEntity<GrupoModificadorResponse> obterGrupoModificador(@PathVariable UUID id) {
        return ResponseEntity.ok(catalogoService.obterGrupoModificador(id));
    }

    @PutMapping({"/grupos-modificadores/{id}", "/catalogo/grupos-modificadores/{id}"})
    public ResponseEntity<GrupoModificadorResponse> atualizarGrupoModificador(
            @PathVariable UUID id,
            @Valid @RequestBody GrupoModificadorRequest request
    ) {
        return ResponseEntity.ok(catalogoService.atualizarGrupoModificador(id, request));
    }

    @DeleteMapping({"/grupos-modificadores/{id}", "/catalogo/grupos-modificadores/{id}"})
    public ResponseEntity<Void> arquivarGrupoModificador(@PathVariable UUID id) {
        catalogoService.arquivarGrupoModificador(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping({"/grupos-modificadores/{id}/modificadores", "/catalogo/grupos-modificadores/{id}/modificadores"})
    public ResponseEntity<ModificadorResponse> criarModificador(
            @PathVariable UUID id,
            @Valid @RequestBody ModificadorRequest request
    ) {
        ModificadorResponse criado = catalogoService.criarModificador(id, request);
        return ResponseEntity.created(URI.create("/api/v1/modificadores/" + criado.id())).body(criado);
    }

    @PutMapping({"/modificadores/{id}", "/catalogo/modificadores/{id}"})
    public ResponseEntity<ModificadorResponse> atualizarModificador(
            @PathVariable UUID id,
            @Valid @RequestBody ModificadorRequest request
    ) {
        return ResponseEntity.ok(catalogoService.atualizarModificador(id, request));
    }

    @DeleteMapping({"/modificadores/{id}", "/catalogo/modificadores/{id}"})
    public ResponseEntity<Void> arquivarModificador(@PathVariable UUID id) {
        catalogoService.arquivarModificador(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping({"/modificadores/{id}/preco", "/catalogo/modificadores/{id}/preco"})
    public ResponseEntity<ModificadorResponse> alterarPrecoModificador(
            @PathVariable UUID id,
            @Valid @RequestBody AlterarPrecoModificadorRequest request
    ) {
        return ResponseEntity.ok(catalogoService.alterarPrecoModificador(id, request));
    }

    @PatchMapping({"/modificadores/{id}/disponibilidade", "/catalogo/modificadores/{id}/disponibilidade"})
    public ResponseEntity<DisponibilidadeResponse> alterarDisponibilidadeModificador(
            @PathVariable UUID id,
            @Valid @RequestBody DisponibilidadeToggleRequest request
    ) {
        return ResponseEntity.ok(catalogoService.alterarDisponibilidadeModificador(id, request));
    }

    @GetMapping({"/modificadores/{id}/historico-precos", "/catalogo/modificadores/{id}/historico-precos"})
    public ResponseEntity<List<HistoricoPrecoResponse>> historicoPrecosModificador(@PathVariable UUID id) {
        return ResponseEntity.ok(catalogoService.historicoPrecosModificador(id));
    }

    // ──────────────────────────── PRODUTO & COMBO ────────────────────────────
    @GetMapping({"/produtos", "/catalogo/produtos"})
    public ResponseEntity<PageResponse<ProdutoResumoResponse>> listarProdutos(
            @RequestParam(required = false) UUID categoriaId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) TipoProduto tipo,
            @RequestParam(required = false) Boolean ativo,
            @RequestParam(required = false) Boolean disponivel,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(catalogoService.listarProdutos(categoriaId, q, tipo, ativo, disponivel, page, size));
    }

    @PostMapping({"/produtos", "/catalogo/produtos"})
    public ResponseEntity<ProdutoDetalheResponse> criarProduto(@Valid @RequestBody ProdutoRequest request) {
        ProdutoDetalheResponse criado = catalogoService.criarProduto(request);
        return ResponseEntity.created(URI.create("/api/v1/produtos/" + criado.id())).body(criado);
    }

    @GetMapping({"/produtos/{id}", "/catalogo/produtos/{id}"})
    public ResponseEntity<ProdutoDetalheResponse> obterProduto(@PathVariable UUID id) {
        return ResponseEntity.ok(catalogoService.obterProduto(id));
    }

    @PutMapping({"/produtos/{id}", "/catalogo/produtos/{id}"})
    public ResponseEntity<ProdutoDetalheResponse> atualizarProduto(
            @PathVariable UUID id,
            @Valid @RequestBody ProdutoRequest request
    ) {
        return ResponseEntity.ok(catalogoService.atualizarProduto(id, request));
    }

    @DeleteMapping({"/produtos/{id}", "/catalogo/produtos/{id}"})
    public ResponseEntity<Void> arquivarProduto(@PathVariable UUID id) {
        catalogoService.arquivarProduto(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping({"/produtos/{id}/preco", "/catalogo/produtos/{id}/preco"})
    public ResponseEntity<ProdutoDetalheResponse> alterarPrecoProduto(
            @PathVariable UUID id,
            @Valid @RequestBody AlterarPrecoProdutoRequest request
    ) {
        return ResponseEntity.ok(catalogoService.alterarPrecoProduto(id, request));
    }

    @PatchMapping({"/produtos/{id}/disponibilidade", "/catalogo/produtos/{id}/disponibilidade"})
    public ResponseEntity<DisponibilidadeResponse> alterarDisponibilidadeProduto(
            @PathVariable UUID id,
            @Valid @RequestBody DisponibilidadeToggleRequest request
    ) {
        return ResponseEntity.ok(catalogoService.alterarDisponibilidadeProduto(id, request));
    }

    @PutMapping({"/produtos/{id}/grupos-modificadores", "/catalogo/produtos/{id}/grupos-modificadores"})
    public ResponseEntity<ProdutoDetalheResponse> vincularGruposModificadores(
            @PathVariable UUID id,
            @Valid @RequestBody VinculoGruposRequest request
    ) {
        return ResponseEntity.ok(catalogoService.vincularGruposModificadores(id, request));
    }

    @PutMapping({"/produtos/{id}/insumos", "/catalogo/produtos/{id}/insumos"})
    public ResponseEntity<ProdutoDetalheResponse> vincularInsumosProduto(
            @PathVariable UUID id,
            @Valid @RequestBody VinculoInsumosRequest request
    ) {
        return ResponseEntity.ok(catalogoService.vincularInsumosProduto(id, request));
    }

    @PutMapping({"/produtos/{id}/combo-slots", "/catalogo/produtos/{id}/combo-slots"})
    public ResponseEntity<ProdutoDetalheResponse> definirSlotsCombo(
            @PathVariable UUID id,
            @Valid @RequestBody ComboSlotsRequest request
    ) {
        return ResponseEntity.ok(catalogoService.definirSlotsCombo(id, request));
    }

    @GetMapping({"/produtos/{id}/historico-precos", "/catalogo/produtos/{id}/historico-precos"})
    public ResponseEntity<List<HistoricoPrecoResponse>> historicoPrecosProduto(@PathVariable UUID id) {
        return ResponseEntity.ok(catalogoService.historicoPrecosProduto(id));
    }
}
