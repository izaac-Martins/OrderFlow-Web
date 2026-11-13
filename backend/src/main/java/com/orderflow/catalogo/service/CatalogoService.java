package com.orderflow.catalogo.service;

import com.orderflow.catalogo.domain.CatalogoVersao;
import com.orderflow.catalogo.domain.Categoria;
import com.orderflow.catalogo.domain.ComboSlot;
import com.orderflow.catalogo.domain.ComboSlotOpcao;
import com.orderflow.catalogo.domain.EstacaoPreparo;
import com.orderflow.catalogo.domain.GrupoModificador;
import com.orderflow.catalogo.domain.HistoricoPreco;
import com.orderflow.catalogo.domain.Insumo;
import com.orderflow.catalogo.domain.OpcaoModificador;
import com.orderflow.catalogo.domain.Produto;
import com.orderflow.catalogo.domain.ProdutoModificadorGrupo;
import com.orderflow.catalogo.domain.TipoPreco;
import com.orderflow.catalogo.domain.TipoProduto;
import com.orderflow.catalogo.dto.CatalogoDtos.AlterarPrecoModificadorRequest;
import com.orderflow.catalogo.dto.CatalogoDtos.AlterarPrecoProdutoRequest;
import com.orderflow.catalogo.dto.CatalogoDtos.CatalogoCategoriaNode;
import com.orderflow.catalogo.dto.CatalogoDtos.CatalogoEventoStomp;
import com.orderflow.catalogo.dto.CatalogoDtos.CatalogoGrupoNode;
import com.orderflow.catalogo.dto.CatalogoDtos.CatalogoProdutoNode;
import com.orderflow.catalogo.dto.CatalogoDtos.CatalogoResponse;
import com.orderflow.catalogo.dto.CatalogoDtos.CategoriaRequest;
import com.orderflow.catalogo.dto.CatalogoDtos.CategoriaResponse;
import com.orderflow.catalogo.dto.CatalogoDtos.ComboSlotItemRequest;
import com.orderflow.catalogo.dto.CatalogoDtos.ComboSlotOpcaoRequest;
import com.orderflow.catalogo.dto.CatalogoDtos.ComboSlotOpcaoResponse;
import com.orderflow.catalogo.dto.CatalogoDtos.ComboSlotResponse;
import com.orderflow.catalogo.dto.CatalogoDtos.ComboSlotsRequest;
import com.orderflow.catalogo.dto.CatalogoDtos.DisponibilidadeItemEvento;
import com.orderflow.catalogo.dto.CatalogoDtos.DisponibilidadeResponse;
import com.orderflow.catalogo.dto.CatalogoDtos.DisponibilidadeToggleRequest;
import com.orderflow.catalogo.dto.CatalogoDtos.GrupoModificadorRequest;
import com.orderflow.catalogo.dto.CatalogoDtos.GrupoModificadorResponse;
import com.orderflow.catalogo.dto.CatalogoDtos.HistoricoPrecoResponse;
import com.orderflow.catalogo.dto.CatalogoDtos.InsumoDisponibilidadeRequest;
import com.orderflow.catalogo.dto.CatalogoDtos.InsumoDisponibilidadeResponse;
import com.orderflow.catalogo.dto.CatalogoDtos.InsumoRequest;
import com.orderflow.catalogo.dto.CatalogoDtos.InsumoResponse;
import com.orderflow.catalogo.dto.CatalogoDtos.ItemAfetadoResumo;
import com.orderflow.catalogo.dto.CatalogoDtos.ModificadorRequest;
import com.orderflow.catalogo.dto.CatalogoDtos.ModificadorResponse;
import com.orderflow.catalogo.dto.CatalogoDtos.OrdemItemRequest;
import com.orderflow.catalogo.dto.CatalogoDtos.PageResponse;
import com.orderflow.catalogo.dto.CatalogoDtos.ProdutoDetalheResponse;
import com.orderflow.catalogo.dto.CatalogoDtos.ProdutoRequest;
import com.orderflow.catalogo.dto.CatalogoDtos.ProdutoResumoResponse;
import com.orderflow.catalogo.dto.CatalogoDtos.VinculoGrupoItemRequest;
import com.orderflow.catalogo.dto.CatalogoDtos.VinculoGrupoResponse;
import com.orderflow.catalogo.dto.CatalogoDtos.VinculoGruposRequest;
import com.orderflow.catalogo.dto.CatalogoDtos.VinculoInsumosRequest;
import com.orderflow.catalogo.repository.CatalogoVersaoRepository;
import com.orderflow.catalogo.repository.CategoriaRepository;
import com.orderflow.catalogo.repository.ComboSlotOpcaoRepository;
import com.orderflow.catalogo.repository.ComboSlotRepository;
import com.orderflow.catalogo.repository.GrupoModificadorRepository;
import com.orderflow.catalogo.repository.HistoricoPrecoRepository;
import com.orderflow.catalogo.repository.InsumoRepository;
import com.orderflow.catalogo.repository.OpcaoModificadorRepository;
import com.orderflow.catalogo.repository.ProdutoModificadorGrupoRepository;
import com.orderflow.catalogo.repository.ProdutoRepository;
import com.orderflow.shared.error.CodigoErro;
import com.orderflow.shared.error.OrderFlowException;
import com.orderflow.shared.security.TenantContext;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Serviço de domínio e aplicação do Módulo de Catálogo & Cardápio:
 * Categorias (até 2 níveis), Insumos, Grupos/Opções de Modificadores, Produtos, Combos,
 * Cálculo de Disponibilidade em Cascata e Broadcast STOMP em tempo real.
 */
@Service
public class CatalogoService {

    private final TenantContext tenantContext;
    private final CatalogoVersaoRepository catalogoVersaoRepository;
    private final CategoriaRepository categoriaRepository;
    private final InsumoRepository insumoRepository;
    private final GrupoModificadorRepository grupoModificadorRepository;
    private final OpcaoModificadorRepository opcaoModificadorRepository;
    private final ProdutoRepository produtoRepository;
    private final ProdutoModificadorGrupoRepository produtoModificadorGrupoRepository;
    private final ComboSlotRepository comboSlotRepository;
    private final ComboSlotOpcaoRepository comboSlotOpcaoRepository;
    private final HistoricoPrecoRepository historicoPrecoRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public CatalogoService(
            TenantContext tenantContext,
            CatalogoVersaoRepository catalogoVersaoRepository,
            CategoriaRepository categoriaRepository,
            InsumoRepository insumoRepository,
            GrupoModificadorRepository grupoModificadorRepository,
            OpcaoModificadorRepository opcaoModificadorRepository,
            ProdutoRepository produtoRepository,
            ProdutoModificadorGrupoRepository produtoModificadorGrupoRepository,
            ComboSlotRepository comboSlotRepository,
            ComboSlotOpcaoRepository comboSlotOpcaoRepository,
            HistoricoPrecoRepository historicoPrecoRepository,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.tenantContext = tenantContext;
        this.catalogoVersaoRepository = catalogoVersaoRepository;
        this.categoriaRepository = categoriaRepository;
        this.insumoRepository = insumoRepository;
        this.grupoModificadorRepository = grupoModificadorRepository;
        this.opcaoModificadorRepository = opcaoModificadorRepository;
        this.produtoRepository = produtoRepository;
        this.produtoModificadorGrupoRepository = produtoModificadorGrupoRepository;
        this.comboSlotRepository = comboSlotRepository;
        this.comboSlotOpcaoRepository = comboSlotOpcaoRepository;
        this.historicoPrecoRepository = historicoPrecoRepository;
        this.messagingTemplate = messagingTemplate;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 1. CATEGORIAS (Hierarquia máx. 2 níveis)
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<CategoriaResponse> listarCategorias( boolean incluirArquivadas) {
        UUID lojaId = tenantContext.requireCurrentLojaId();
        List<Categoria> todas = incluirArquivadas
                ? categoriaRepository.findByLojaIdOrderByOrdemExibicaoAscNomeAsc(lojaId)
                : categoriaRepository.findByLojaIdAndArquivadoEmIsNullOrderByOrdemExibicaoAscNomeAsc(lojaId);

        Map<UUID, List<Categoria>> filhasPorPai = todas.stream()
                .filter(c -> c.getCategoriaPaiId() != null)
                .collect(Collectors.groupingBy(Categoria::getCategoriaPaiId, LinkedHashMap::new, Collectors.toList()));

        return todas.stream()
                .filter(c -> c.getCategoriaPaiId() == null)
                .map(raiz -> toCategoriaResponse(raiz, filhasPorPai.getOrDefault(raiz.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoriaResponse obterCategoria(UUID id) {
        UUID lojaId = tenantContext.requireCurrentLojaId();
        Categoria cat = buscarCategoriaObrigatoria(lojaId, id);
        List<Categoria> filhas = categoriaRepository.findByLojaIdAndArquivadoEmIsNullOrderByOrdemExibicaoAscNomeAsc(lojaId)
                .stream()
                .filter(c -> id.equals(c.getCategoriaPaiId()))
                .toList();
        return toCategoriaResponse(cat, filhas);
    }

    @Transactional
    public CategoriaResponse criarCategoria(CategoriaRequest req) {
        UUID lojaId = tenantContext.requireCurrentLojaId();
        validarHierarquiaCategoriaPai(lojaId, req.parentId(), null);

        String slug = Categoria.normalizeSlug(req.slug() != null && !req.slug().isBlank() ? req.slug() : req.nome());
        if (categoriaRepository.existsSiblingWithSlug(lojaId, req.parentId(), slug, null)) {
            throw new OrderFlowException(CodigoErro.SLUG_DUPLICADO, "Já existe uma categoria irmã com o slug '" + slug + "'.");
        }

        Categoria nova = new Categoria(
                lojaId,
                req.parentId(),
                req.nome(),
                slug,
                req.ordem() != null ? req.ordem() : 0,
                req.ativo() == null || req.ativo(),
                req.estacaoPreparoPadrao()
        );
        Categoria salva = categoriaRepository.save(nova);
        long versaoCat = incrementarVersaoEPublicarEventoCatalogo(lojaId, "CATEGORIA_ALTERADA", List.of(salva.getId()));
        return toCategoriaResponse(salva, List.of());
    }

    @Transactional
    public CategoriaResponse atualizarCategoria(UUID id, CategoriaRequest req) {
        UUID lojaId = tenantContext.requireCurrentLojaId();
        Categoria cat = buscarCategoriaObrigatoria(lojaId, id);
        validarVersaoObrigatoria(req.versao(), cat.getVersao(), cat.isAtivo() ? "ATIVA" : "INATIVA");

        validarHierarquiaCategoriaPai(lojaId, req.parentId(), id);
        if (req.parentId() != null && categoriaRepository.existsByLojaIdAndCategoriaPaiIdAndArquivadoEmIsNull(lojaId, id)) {
            throw new OrderFlowException(
                    CodigoErro.CATEGORIA_PROFUNDIDADE_EXCEDIDA,
                    "Esta categoria já possui subcategorias e não pode ser transformada em subcategoria (máximo 2 níveis)."
            );
        }

        String slug = Categoria.normalizeSlug(req.slug() != null && !req.slug().isBlank() ? req.slug() : req.nome());
        if (categoriaRepository.existsSiblingWithSlug(lojaId, req.parentId(), slug, id)) {
            throw new OrderFlowException(CodigoErro.SLUG_DUPLICADO, "Já existe uma categoria irmã com o slug '" + slug + "'.");
        }

        cat.setCategoriaPaiId(req.parentId());
        cat.setNome(req.nome());
        cat.setSlug(slug);
        if (req.ordem() != null) {
            cat.setOrdemExibicao(req.ordem());
        }
        if (req.ativo() != null) {
            cat.setAtivo(req.ativo());
        }
        if (req.estacaoPreparoPadrao() != null) {
            cat.setEstacaoPreparoPadrao(req.estacaoPreparoPadrao());
        }

        Categoria salva = categoriaRepository.saveAndFlush(cat);
        incrementarVersaoEPublicarEventoCatalogo(lojaId, "CATEGORIA_ALTERADA", List.of(salva.getId()));
        return obterCategoria(salva.getId());
    }

    @Transactional
    public void reordenarCategorias(List<OrdemItemRequest> itens) {
        UUID lojaId = tenantContext.requireCurrentLojaId();
        List<UUID> idsAlterados = new ArrayList<>();
        for (OrdemItemRequest item : itens) {
            Categoria cat = buscarCategoriaObrigatoria(lojaId, item.id());
            cat.setOrdemExibicao(item.ordem());
            categoriaRepository.save(cat);
            idsAlterados.add(cat.getId());
        }
        incrementarVersaoEPublicarEventoCatalogo(lojaId, "CATEGORIA_ALTERADA", idsAlterados);
    }

    @Transactional
    public void arquivarCategoria(UUID id) {
        UUID lojaId = tenantContext.requireCurrentLojaId();
        Categoria cat = buscarCategoriaObrigatoria(lojaId, id);

        if (produtoRepository.existsByLojaIdAndCategoriaIdAndAtivoTrueAndArquivadoEmIsNull(lojaId, id)) {
            throw new OrderFlowException(
                    CodigoErro.CATEGORIA_COM_PRODUTOS_ATIVOS,
                    "A categoria possui produtos ativos vinculados e não pode ser arquivada."
            );
        }
        if (categoriaRepository.existsByLojaIdAndCategoriaPaiIdAndArquivadoEmIsNull(lojaId, id)) {
            throw new OrderFlowException(
                    CodigoErro.CATEGORIA_COM_PRODUTOS_ATIVOS,
                    "A categoria possui subcategorias ativas e não pode ser arquivada."
            );
        }

        cat.arquivar(Instant.now());
        categoriaRepository.save(cat);
        incrementarVersaoEPublicarEventoCatalogo(lojaId, "CATEGORIA_ALTERADA", List.of(id));
    }

    private void validarHierarquiaCategoriaPai(UUID lojaId, UUID parentId, UUID categoriaAtualId) {
        if (parentId == null) {
            return;
        }
        if (parentId.equals(categoriaAtualId)) {
            throw new OrderFlowException(CodigoErro.CATEGORIA_PROFUNDIDADE_EXCEDIDA, "Uma categoria não pode ser pai de si mesma.");
        }
        Categoria pai = buscarCategoriaObrigatoria(lojaId, parentId);
        if (pai.isArquivada()) {
            throw new OrderFlowException(CodigoErro.CATEGORIA_ARQUIVADA, "Categoria pai informada está arquivada.");
        }
        if (pai.getCategoriaPaiId() != null) {
            throw new OrderFlowException(
                    CodigoErro.CATEGORIA_PROFUNDIDADE_EXCEDIDA,
                    "Hierarquia de categorias suporta no máximo 2 níveis (Categoria -> Subcategoria)."
            );
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 2. INSUMOS & DISPONIBILIDADE EM CASCATA (STOMP)
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<InsumoResponse> listarInsumos(Boolean disponivel) {
        UUID lojaId = tenantContext.requireCurrentLojaId();
        List<Insumo> insumos = disponivel == null
                ? insumoRepository.findByLojaIdOrderByNomeAsc(lojaId)
                : insumoRepository.findByLojaIdAndDisponivelOrderByNomeAsc(lojaId, disponivel);
        return insumos.stream().map(this::toInsumoResponse).toList();
    }

    @Transactional
    public InsumoResponse criarInsumo(InsumoRequest req) {
        UUID lojaId = tenantContext.requireCurrentLojaId();
        if (insumoRepository.existsByLojaIdAndNomeIgnoreCase(lojaId, req.nome().trim())) {
            throw new OrderFlowException(CodigoErro.NOME_DUPLICADO, "Já existe um insumo com este nome na loja.");
        }
        Insumo novo = new Insumo(
                lojaId,
                req.nome(),
                req.unidade(),
                req.quantidadeAtual() != null ? req.quantidadeAtual() : 0L,
                req.estoqueMinimo() != null ? req.estoqueMinimo() : 0L,
                req.disponivel() == null || req.disponivel()
        );
        Insumo salvo = insumoRepository.save(novo);
        incrementarVersaoCatalogo(lojaId);
        return toInsumoResponse(salvo);
    }

    @Transactional
    public InsumoResponse atualizarInsumo(UUID id, InsumoRequest req) {
        UUID lojaId = tenantContext.requireCurrentLojaId();
        Insumo insumo = buscarInsumoObrigatorio(lojaId, id);
        validarVersaoObrigatoria(req.versao(), insumo.getVersao(), insumo.isDisponivel() ? "DISPONIVEL" : "INDISPONIVEL");

        if (insumoRepository.existsByLojaIdAndNomeIgnoreCaseAndIdNot(lojaId, req.nome().trim(), id)) {
            throw new OrderFlowException(CodigoErro.NOME_DUPLICADO, "Já existe outro insumo com este nome na loja.");
        }
        insumo.setNome(req.nome());
        insumo.setUnidadeMedida(req.unidade());
        if (req.quantidadeAtual() != null) {
            insumo.setQuantidadeAtual(req.quantidadeAtual());
        }
        if (req.estoqueMinimo() != null) {
            insumo.setEstoqueMinimo(req.estoqueMinimo());
        }
        Insumo salvo = insumoRepository.saveAndFlush(insumo);
        incrementarVersaoCatalogo(lojaId);
        return toInsumoResponse(salvo);
    }

    /**
     * Pausa/reativa um insumo, recalcula em cascata todos os modificadores, produtos simples
     * e combos dependentes e publica imediatamente no tópico STOMP {@code /topic/lojas/{lojaId}/disponibilidade}.
     */
    @Transactional
    public InsumoDisponibilidadeResponse alterarDisponibilidadeInsumo(UUID id, InsumoDisponibilidadeRequest req) {
        UUID lojaId = tenantContext.requireCurrentLojaId();
        Insumo insumo = buscarInsumoObrigatorio(lojaId, id);

        insumo.alterarDisponibilidade(req.disponivel(), req.motivo());
        Insumo salvo = insumoRepository.saveAndFlush(insumo);

        SnapshotDisponibilidadeLoja snapshot = calcularSnapshotDisponibilidade(lojaId);

        // Identifica modificadores que dependem deste insumo
        List<ItemAfetadoResumo> modsAfetados = snapshot.modificadoresPorId().values().stream()
                .filter(m -> m.getInsumoIds().contains(id))
                .map(m -> new ItemAfetadoResumo(m.getId(), m.getNome(), snapshot.modificadorDisponivel(m.getId())))
                .toList();

        Set<UUID> modIdsAfetados = modsAfetados.stream().map(ItemAfetadoResumo::id).collect(Collectors.toSet());
        Set<UUID> gruposDosModsAfetados = snapshot.modificadoresPorId().values().stream()
                .filter(m -> modIdsAfetados.contains(m.getId()))
                .map(OpcaoModificador::getGrupoId)
                .collect(Collectors.toSet());

        // Identifica produtos que dependem diretamente do insumo, ou de grupos afetados, ou combos que os referenciam
        Set<UUID> produtosImpactadosIds = new HashSet<>();
        for (Produto p : snapshot.produtosPorId().values()) {
            if (p.getInsumoIds().contains(id)) {
                produtosImpactadosIds.add(p.getId());
            }
        }
        for (ProdutoModificadorGrupo v : snapshot.vinculosGrupos()) {
            if (gruposDosModsAfetados.contains(v.getGrupoId())) {
                produtosImpactadosIds.add(v.getProdutoId());
            }
        }
        for (ComboSlot slot : snapshot.slotsPorCombo().values().stream().flatMap(List::stream).toList()) {
            List<ComboSlotOpcao> opcoes = snapshot.opcoesPorSlot().getOrDefault(slot.getId(), List.of());
            if (opcoes.stream().anyMatch(o -> produtosImpactadosIds.contains(o.getProdutoId()))) {
                produtosImpactadosIds.add(slot.getProdutoComboId());
            }
        }

        List<ItemAfetadoResumo> prodsAfetados = produtosImpactadosIds.stream()
                .map( snapshot.produtosPorId()::get)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(Produto::getNome))
                .map(p -> new ItemAfetadoResumo(p.getId(), p.getNome(), snapshot.produtoDisponivel(p.getId())))
                .toList();

        List<DisponibilidadeItemEvento> itensEvento = new ArrayList<>();
        for (ItemAfetadoResumo m : modsAfetados) {
            itensEvento.add(new DisponibilidadeItemEvento(m.id(), "MODIFICADOR", m.disponivel()));
        }
        for (ItemAfetadoResumo p : prodsAfetados) {
            itensEvento.add(new DisponibilidadeItemEvento(p.id(), "PRODUTO", p.disponivel()));
        }

        List<UUID> idsEvento = new ArrayList<>();
        idsEvento.add(salvo.getId());
        prodsAfetados.forEach(p -> idsEvento.add(p.id()));
        modsAfetados.forEach(m -> idsEvento.add(m.id()));

        long versaoCat = incrementarVersaoCatalogo(lojaId);
        publicarEventoDisponibilidadeStomp(lojaId, versaoCat, idsEvento, itensEvento);

        return new InsumoDisponibilidadeResponse(
                toInsumoResponse(salvo),
                versaoCat,
                prodsAfetados,
                modsAfetados
        );
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 3. GRUPOS E OPÇÕES DE MODIFICADORES
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<GrupoModificadorResponse> listarGruposModificadores() {
        UUID lojaId = tenantContext.requireCurrentLojaId();
        SnapshotDisponibilidadeLoja snapshot = calcularSnapshotDisponibilidade(lojaId);
        List<GrupoModificador> grupos = grupoModificadorRepository.findByLojaIdAndArquivadoEmIsNullOrderByOrdemAscNomeAsc(lojaId);
        return grupos.stream()
                .map(g -> toGrupoModificadorResponse(lojaId, g, snapshot))
                .toList();
    }

    @Transactional(readOnly = true)
    public GrupoModificadorResponse obterGrupoModificador(UUID id) {
        UUID lojaId = tenantContext.requireCurrentLojaId();
        GrupoModificador grupo = buscarGrupoObrigatorio(lojaId, id);
        SnapshotDisponibilidadeLoja snapshot = calcularSnapshotDisponibilidade(lojaId);
        return toGrupoModificadorResponse(lojaId, grupo, snapshot);
    }

    @Transactional
    public GrupoModificadorResponse criarGrupoModificador(GrupoModificadorRequest req) {
        UUID lojaId = tenantContext.requireCurrentLojaId();
        validarLimitesSelecao(req.minSelecao(), req.maxSelecao());

        if (grupoModificadorRepository.existsByLojaIdAndNomeIgnoreCaseAndArquivadoEmIsNull(lojaId, req.nome().trim())) {
            throw new OrderFlowException(CodigoErro.NOME_DUPLICADO, "Já existe um grupo de modificadores com este nome na loja.");
        }

        if (req.modificadores() != null && req.maxSelecao() == 1) {
            long qtdPadrao = req.modificadores().stream().filter(m -> Boolean.TRUE.equals(m.padrao())).count();
            if (qtdPadrao > 1) {
                throw new OrderFlowException(
                        CodigoErro.LIMITES_SELECAO_INVALIDOS,
                        "Grupo com seleção máxima igual a 1 permite no máximo uma opção marcada como padrão."
                );
            }
        }

        GrupoModificador grupo = new GrupoModificador(
                lojaId,
                req.nome(),
                req.nomeInterno(),
                req.minSelecao(),
                req.maxSelecao(),
                Boolean.TRUE.equals(req.permiteRepeticao()),
                req.ordem() != null ? req.ordem() : 0,
                req.ativo() == null || req.ativo()
        );
        GrupoModificador salvo = grupoModificadorRepository.save(grupo);

        if (req.modificadores() != null) {
            Set<String> nomesVistos = new HashSet<>();
            for (ModificadorRequest modReq : req.modificadores()) {
                String chaveNome = modReq.nome().trim().toLowerCase(Locale.ROOT);
                if (!nomesVistos.add(chaveNome)) {
                    throw new OrderFlowException(CodigoErro.NOME_DUPLICADO, "Nome de modificador duplicado no mesmo grupo: " + modReq.nome());
                }
                criarOpcaoModificadorInterno(lojaId, salvo.getId(), modReq);
            }
        }

        incrementarVersaoEPublicarEventoCatalogo(lojaId, "GRUPO_ALTERADO", List.of(salvo.getId()));
        SnapshotDisponibilidadeLoja snapshot = calcularSnapshotDisponibilidade(lojaId);
        return toGrupoModificadorResponse(lojaId, salvo, snapshot);
    }

    @Transactional
    public GrupoModificadorResponse atualizarGrupoModificador(UUID id, GrupoModificadorRequest req) {
        UUID lojaId = tenantContext.requireCurrentLojaId();
        GrupoModificador grupo = buscarGrupoObrigatorio(lojaId, id);
        validarVersaoObrigatoria(req.versao(), grupo.getVersao(), grupo.isAtivo() ? "ATIVO" : "INATIVO");
        validarLimitesSelecao(req.minSelecao(), req.maxSelecao());

        if (grupoModificadorRepository.existsByLojaIdAndNomeIgnoreCaseAndIdNotAndArquivadoEmIsNull(lojaId, req.nome().trim(), id)) {
            throw new OrderFlowException(CodigoErro.NOME_DUPLICADO, "Já existe outro grupo de modificadores com este nome na loja.");
        }

        grupo.setNome(req.nome());
        grupo.setNomeInterno(req.nomeInterno());
        grupo.setMinSelecao(req.minSelecao());
        grupo.setMaxSelecao(req.maxSelecao());
        if (req.permiteRepeticao() != null) {
            grupo.setPermiteRepeticao(req.permiteRepeticao());
        }
        if (req.ordem() != null) {
            grupo.setOrdem(req.ordem());
        }
        if (req.ativo() != null) {
            grupo.setAtivo(req.ativo());
        }

        GrupoModificador salvo = grupoModificadorRepository.saveAndFlush(grupo);
        incrementarVersaoEPublicarEventoCatalogo(lojaId, "GRUPO_ALTERADO", List.of(salvo.getId()));
        SnapshotDisponibilidadeLoja snapshot = calcularSnapshotDisponibilidade(lojaId);
        return toGrupoModificadorResponse(lojaId, salvo, snapshot);
    }

    @Transactional
    public void arquivarGrupoModificador(UUID id) {
        UUID lojaId = tenantContext.requireCurrentLojaId();
        GrupoModificador grupo = buscarGrupoObrigatorio(lojaId, id);
        long emUso = produtoModificadorGrupoRepository.countActiveProductsUsingGrupo(lojaId, id);
        if (emUso > 0) {
            throw new OrderFlowException(
                    CodigoErro.GRUPO_EM_USO,
                    "Este grupo de modificadores está vinculado a " + emUso + " produto(s) ativo(s) e não pode ser arquivado."
            );
        }
        grupo.arquivar(Instant.now());
        grupoModificadorRepository.save(grupo);
        incrementarVersaoEPublicarEventoCatalogo(lojaId, "GRUPO_ALTERADO", List.of(id));
    }

    @Transactional
    public ModificadorResponse criarModificador(UUID grupoId, ModificadorRequest req) {
        UUID lojaId = tenantContext.requireCurrentLojaId();
        GrupoModificador grupo = buscarGrupoObrigatorio(lojaId, grupoId);
        if (opcaoModificadorRepository.existsByGrupoIdAndNomeIgnoreCaseAndArquivadoEmIsNull(grupo.getId(), req.nome().trim())) {
            throw new OrderFlowException(CodigoErro.NOME_DUPLICADO, "Já existe um modificador com este nome no grupo.");
        }

        OpcaoModificador salvo = criarOpcaoModificadorInterno(lojaId, grupo.getId(), req);
        incrementarVersaoEPublicarEventoCatalogo(lojaId, "GRUPO_ALTERADO", List.of(grupo.getId(), salvo.getId()));
        SnapshotDisponibilidadeLoja snapshot = calcularSnapshotDisponibilidade(lojaId);
        return toModificadorResponse(salvo, snapshot.modificadorDisponivel(salvo.getId()), snapshot.motivoModificador(salvo));
    }

    @Transactional
    public ModificadorResponse atualizarModificador(UUID id, ModificadorRequest req) {
        UUID lojaId = tenantContext.requireCurrentLojaId();
        OpcaoModificador mod = buscarModificadorObrigatorio(lojaId, id);
        validarVersaoObrigatoria(req.versao(), mod.getVersao(), mod.isAtivo() ? "ATIVO" : "INATIVO");

        if (opcaoModificadorRepository.existsByGrupoIdAndNomeIgnoreCaseAndIdNotAndArquivadoEmIsNull(mod.getGrupoId(), req.nome().trim(), id)) {
            throw new OrderFlowException(CodigoErro.NOME_DUPLICADO, "Já existe outro modificador com este nome no grupo.");
        }

        mod.setNome(req.nome());
        if (req.padrao() != null) {
            mod.setPadrao(req.padrao());
        }
        if (req.maxQuantidade() != null) {
            mod.setMaxQuantidade(req.maxQuantidade());
        }
        if (req.ordem() != null) {
            mod.setOrdem(req.ordem());
        }
        if (req.ativo() != null) {
            mod.setAtivo(req.ativo());
        }
        if (req.disponivel() != null) {
            mod.alterarPausa(!req.disponivel(), null);
        }
        if (req.insumoIds() != null) {
            validarInsumosPertencemALoja(lojaId, req.insumoIds());
            mod.setInsumoIds(new HashSet<>(req.insumoIds()));
        }

        OpcaoModificador salvo = opcaoModificadorRepository.saveAndFlush(mod);
        incrementarVersaoEPublicarEventoCatalogo(lojaId, "GRUPO_ALTERADO", List.of(salvo.getId()));
        SnapshotDisponibilidadeLoja snapshot = calcularSnapshotDisponibilidade(lojaId);
        return toModificadorResponse(salvo, snapshot.modificadorDisponivel(salvo.getId()), snapshot.motivoModificador(salvo));
    }

    @Transactional
    public ModificadorResponse alterarPrecoModificador(UUID id, AlterarPrecoModificadorRequest req) {
        UUID lojaId = tenantContext.requireCurrentLojaId();
        OpcaoModificador mod = buscarModificadorObrigatorio(lojaId, id);
        validarVersaoObrigatoria(req.versao(), mod.getVersao(), mod.isAtivo() ? "ATIVO" : "INATIVO");

        TipoPreco novoTipo = req.tipoPreco() != null ? req.tipoPreco() : TipoPreco.FIXO;
        long novoValor = novoTipo == TipoPreco.NENHUM ? 0L : req.valorPreco();
        validarPrecoModificador(novoTipo, novoValor);

        HistoricoPreco hist = new HistoricoPreco(
                lojaId,
                "MODIFICADOR",
                mod.getId(),
                mod.getTipoPreco(),
                mod.getValorPreco(),
                novoTipo,
                novoValor,
                req.motivo()
        );
        historicoPrecoRepository.save(hist);

        mod.setTipoPreco(novoTipo);
        mod.setValorPreco(novoValor);
        OpcaoModificador salvo = opcaoModificadorRepository.saveAndFlush(mod);
        incrementarVersaoEPublicarEventoCatalogo(lojaId, "PRECO_ALTERADO", List.of(salvo.getId()));
        SnapshotDisponibilidadeLoja snapshot = calcularSnapshotDisponibilidade(lojaId);
        return toModificadorResponse(salvo, snapshot.modificadorDisponivel(salvo.getId()), snapshot.motivoModificador(salvo));
    }

    @Transactional
    public DisponibilidadeResponse alterarDisponibilidadeModificador(UUID id, DisponibilidadeToggleRequest req) {
        UUID lojaId = tenantContext.requireCurrentLojaId();
        OpcaoModificador mod = buscarModificadorObrigatorio(lojaId, id);
        boolean pausado = req.resolvePausado();
        mod.alterarPausa(pausado, req.motivo());
        OpcaoModificador salvo = opcaoModificadorRepository.saveAndFlush(mod);

        SnapshotDisponibilidadeLoja snapshot = calcularSnapshotDisponibilidade(lojaId);
        boolean disponivelEfetivo = snapshot.modificadorDisponivel(salvo.getId());
        long versaoCat = incrementarVersaoCatalogo(lojaId);

        publicarEventoDisponibilidadeStomp(
                lojaId,
                versaoCat,
                List.of(salvo.getId()),
                List.of(new DisponibilidadeItemEvento(salvo.getId(), "MODIFICADOR", disponivelEfetivo))
        );

        return new DisponibilidadeResponse(
                salvo.getId(),
                salvo.isPausado(),
                disponivelEfetivo,
                snapshot.motivoModificador(salvo),
                versaoCat
        );
    }

    @Transactional
    public void arquivarModificador(UUID id) {
        UUID lojaId = tenantContext.requireCurrentLojaId();
        OpcaoModificador mod = buscarModificadorObrigatorio(lojaId, id);
        mod.arquivar(Instant.now());
        opcaoModificadorRepository.save(mod);
        incrementarVersaoEPublicarEventoCatalogo(lojaId, "GRUPO_ALTERADO", List.of(id));
    }

    @Transactional(readOnly = true)
    public List<HistoricoPrecoResponse> historicoPrecosModificador(UUID id) {
        UUID lojaId = tenantContext.requireCurrentLojaId();
        buscarModificadorObrigatorio(lojaId, id);
        return historicoPrecoRepository.findByLojaIdAndEntidadeTipoAndEntidadeIdOrderByVigenteDesdeDesc(lojaId, "MODIFICADOR", id)
                .stream()
                .map(this::toHistoricoPrecoResponse)
                .toList();
    }

    private OpcaoModificador criarOpcaoModificadorInterno(UUID lojaId, UUID grupoId, ModificadorRequest req) {
        TipoPreco tipoPreco = req.tipoPreco() != null ? req.tipoPreco() : TipoPreco.FIXO;
        long valor = req.valorPreco() != null ? req.valorPreco() : 0L;
        validarPrecoModificador(tipoPreco, valor);

        OpcaoModificador mod = new OpcaoModificador(
                lojaId,
                grupoId,
                req.nome(),
                tipoPreco,
                valor,
                Boolean.TRUE.equals(req.padrao()),
                req.maxQuantidade() != null ? req.maxQuantidade() : 1,
                req.ordem() != null ? req.ordem() : 0,
                req.ativo() == null || req.ativo()
        );
        if (Boolean.FALSE.equals(req.disponivel())) {
            mod.alterarPausa(true, "Criado como indisponível");
        }
        if (req.insumoIds() != null && !req.insumoIds().isEmpty()) {
            validarInsumosPertencemALoja(lojaId, req.insumoIds());
            mod.setInsumoIds(new HashSet<>(req.insumoIds()));
        }
        return opcaoModificadorRepository.save(mod);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 4. PRODUTOS & COMBOS
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public PageResponse<ProdutoResumoResponse> listarProdutos(
            UUID categoriaId,
            String q,
            TipoProduto tipo,
            Boolean ativo,
            Boolean disponivel,
            int page,
            int size
    ) {
        UUID lojaId = tenantContext.requireCurrentLojaId();
        SnapshotDisponibilidadeLoja snapshot = calcularSnapshotDisponibilidade(lojaId);

        List<ProdutoResumoResponse> filtrados = produtoRepository.findByLojaIdAndArquivadoEmIsNullOrderByOrdemAscNomeAsc(lojaId)
                .stream()
                .filter(p -> categoriaId == null || categoriaId.equals(p.getCategoriaId()))
                .filter(p -> tipo == null || tipo == p.getTipo())
                .filter(p -> ativo == null || ativo == p.isAtivo())
                .filter(p -> q == null || q.isBlank()
                        || p.getNome().toLowerCase(Locale.ROOT).contains(q.trim().toLowerCase(Locale.ROOT))
                        || p.getSku().toLowerCase(Locale.ROOT).contains(q.trim().toLowerCase(Locale.ROOT)))
                .map(p -> toProdutoResumoResponse(p, snapshot.produtoDisponivel(p.getId())))
                .filter(r -> disponivel == null || disponivel == r.disponivel())
                .toList();

        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(100, size));
        int fromIndex = Math.min(safePage * safeSize, filtrados.size());
        int toIndex = Math.min(fromIndex + safeSize, filtrados.size());
        List<ProdutoResumoResponse> pagina = filtrados.subList(fromIndex, toIndex);
        int totalPages = (int) Math.ceil((double) filtrados.size() / safeSize);

        return new PageResponse<>(pagina, safePage, safeSize, filtrados.size(), totalPages);
    }

    @Transactional(readOnly = true)
    public ProdutoDetalheResponse obterProduto(UUID id) {
        UUID lojaId = tenantContext.requireCurrentLojaId();
        Produto produto = buscarProdutoObrigatorio(lojaId, id);
        SnapshotDisponibilidadeLoja snapshot = calcularSnapshotDisponibilidade(lojaId);
        return toProdutoDetalheResponse(lojaId, produto, snapshot);
    }

    @Transactional
    public ProdutoDetalheResponse criarProduto(ProdutoRequest req) {
        UUID lojaId = tenantContext.requireCurrentLojaId();
        if (req.precoBaseCentavos() == null || req.precoBaseCentavos() < 0) {
            throw new OrderFlowException(CodigoErro.PRECO_INVALIDO, "O preço base em centavos deve ser maior ou igual a zero.");
        }

        Categoria categoria = buscarCategoriaObrigatoria(lojaId, req.categoriaId());
        if (categoria.isArquivada()) {
            throw new OrderFlowException(CodigoErro.CATEGORIA_ARQUIVADA, "Não é permitido vincular produto a uma categoria arquivada.");
        }

        String sku = Produto.normalizeSku(req.sku());
        if (produtoRepository.existsByLojaIdAndSkuIgnoreCase(lojaId, sku)) {
            throw new OrderFlowException(CodigoErro.SKU_DUPLICADO, "Já existe um produto com o SKU '" + sku + "' nesta loja.");
        }

        Produto produto = new Produto(
                lojaId,
                categoria.getId(),
                sku,
                req.nome(),
                req.descricao(),
                req.imagemUrl(),
                req.tipo() != null ? req.tipo() : TipoProduto.SIMPLES,
                req.precoBaseCentavos(),
                req.estacaoPreparo(),
                req.ativo() == null || req.ativo(),
                req.ordem() != null ? req.ordem() : 0
        );
        if (Boolean.FALSE.equals(req.disponivel())) {
            produto.alterarPausa(true, "Criado como indisponível");
        }

        Produto salvo = produtoRepository.save(produto);
        historicoPrecoRepository.save(new HistoricoPreco(
                lojaId,
                "PRODUTO",
                salvo.getId(),
                null,
                null,
                TipoPreco.FIXO,
                salvo.getPrecoBaseCentavos(),
                "Cadastro inicial do produto"
        ));

        incrementarVersaoEPublicarEventoCatalogo(lojaId, "PRODUTO_ALTERADO", List.of(salvo.getId()));
        SnapshotDisponibilidadeLoja snapshot = calcularSnapshotDisponibilidade(lojaId);
        return toProdutoDetalheResponse(lojaId, salvo, snapshot);
    }

    @Transactional
    public ProdutoDetalheResponse atualizarProduto(UUID id, ProdutoRequest req) {
        UUID lojaId = tenantContext.requireCurrentLojaId();
        Produto produto = buscarProdutoObrigatorio(lojaId, id);
        validarVersaoObrigatoria(req.versao(), produto.getVersao(), produto.isAtivo() ? "ATIVO" : "INATIVO");

        Categoria categoria = buscarCategoriaObrigatoria(lojaId, req.categoriaId());
        if (categoria.isArquivada()) {
            throw new OrderFlowException(CodigoErro.CATEGORIA_ARQUIVADA, "Não é permitido vincular produto a uma categoria arquivada.");
        }

        String sku = Produto.normalizeSku(req.sku());
        if (produtoRepository.existsByLojaIdAndSkuIgnoreCaseAndIdNot(lojaId, sku, id)) {
            throw new OrderFlowException(CodigoErro.SKU_DUPLICADO, "Já existe outro produto com o SKU '" + sku + "' nesta loja.");
        }

        if (req.precoBaseCentavos() != null && req.precoBaseCentavos() < 0) {
            throw new OrderFlowException(CodigoErro.PRECO_INVALIDO, "O preço base em centavos não pode ser negativo.");
        }

        produto.setCategoriaId(categoria.getId());
        produto.setSku(sku);
        produto.setNome(req.nome());
        produto.setDescricao(req.descricao());
        produto.setImagemUrl(req.imagemUrl());
        produto.setEstacaoPreparo(req.estacaoPreparo());
        if (req.ativo() != null) {
            produto.setAtivo(req.ativo());
        }
        if (req.disponivel() != null) {
            produto.alterarPausa(!req.disponivel(), null);
        }
        if (req.ordem() != null) {
            produto.setOrdem(req.ordem());
        }

        Produto salvo = produtoRepository.saveAndFlush(produto);
        incrementarVersaoEPublicarEventoCatalogo(lojaId, "PRODUTO_ALTERADO", List.of(salvo.getId()));
        SnapshotDisponibilidadeLoja snapshot = calcularSnapshotDisponibilidade(lojaId);
        return toProdutoDetalheResponse(lojaId, salvo, snapshot);
    }

    @Transactional
    public ProdutoDetalheResponse alterarPrecoProduto(UUID id, AlterarPrecoProdutoRequest req) {
        UUID lojaId = tenantContext.requireCurrentLojaId();
        Produto produto = buscarProdutoObrigatorio(lojaId, id);
        validarVersaoObrigatoria(req.versao(), produto.getVersao(), produto.isAtivo() ? "ATIVO" : "INATIVO");

        if (req.precoBaseCentavos() < 0) {
            throw new OrderFlowException(CodigoErro.PRECO_INVALIDO, "O preço base do produto não pode ser negativo.");
        }

        HistoricoPreco hist = new HistoricoPreco(
                lojaId,
                "PRODUTO",
                produto.getId(),
                TipoPreco.FIXO,
                produto.getPrecoBaseCentavos(),
                TipoPreco.FIXO,
                req.precoBaseCentavos(),
                req.motivo()
        );
        historicoPrecoRepository.save(hist);

        produto.setPrecoBaseCentavos(req.precoBaseCentavos());
        Produto salvo = produtoRepository.saveAndFlush(produto);
        incrementarVersaoEPublicarEventoCatalogo(lojaId, "PRECO_ALTERADO", List.of(salvo.getId()));
        SnapshotDisponibilidadeLoja snapshot = calcularSnapshotDisponibilidade(lojaId);
        return toProdutoDetalheResponse(lojaId, salvo, snapshot);
    }

    @Transactional
    public DisponibilidadeResponse alterarDisponibilidadeProduto(UUID id, DisponibilidadeToggleRequest req) {
        UUID lojaId = tenantContext.requireCurrentLojaId();
        Produto produto = buscarProdutoObrigatorio(lojaId, id);
        boolean pausado = req.resolvePausado();
        produto.alterarPausa(pausado, req.motivo());
        Produto salvo = produtoRepository.saveAndFlush(produto);

        SnapshotDisponibilidadeLoja snapshot = calcularSnapshotDisponibilidade(lojaId);
        boolean disponivelEfetivo = snapshot.produtoDisponivel(salvo.getId());
        long versaoCat = incrementarVersaoCatalogo(lojaId);

        publicarEventoDisponibilidadeStomp(
                lojaId,
                versaoCat,
                List.of(salvo.getId()),
                List.of(new DisponibilidadeItemEvento(salvo.getId(), "PRODUTO", disponivelEfetivo))
        );

        return new DisponibilidadeResponse(
                salvo.getId(),
                salvo.isPausado(),
                disponivelEfetivo,
                snapshot.motivoProduto(salvo),
                versaoCat
        );
    }

    @Transactional
    public ProdutoDetalheResponse vincularGruposModificadores(UUID id, VinculoGruposRequest req) {
        UUID lojaId = tenantContext.requireCurrentLojaId();
        Produto produto = buscarProdutoObrigatorio(lojaId, id);
        if (req.versao() != null) {
            validarVersaoObrigatoria(req.versao(), produto.getVersao(), produto.isAtivo() ? "ATIVO" : "INATIVO");
        }

        produtoModificadorGrupoRepository.deleteByLojaIdAndProdutoId(lojaId, id);

        Set<UUID> gruposVistos = new HashSet<>();
        for (VinculoGrupoItemRequest item : req.grupos()) {
            if (!gruposVistos.add(item.grupoId())) {
                throw new OrderFlowException(CodigoErro.LIMITES_SELECAO_INVALIDOS, "Grupo de modificadores repetido na vinculação.");
            }
            GrupoModificador grupo = buscarGrupoObrigatorio(lojaId, item.grupoId());
            int minEfetivo = item.minSelecaoOverride() != null ? item.minSelecaoOverride() : grupo.getMinSelecao();
            int maxEfetivo = item.maxSelecaoOverride() != null ? item.maxSelecaoOverride() : grupo.getMaxSelecao();
            validarLimitesSelecao(minEfetivo, maxEfetivo);

            ProdutoModificadorGrupo vinculo = new ProdutoModificadorGrupo(
                    lojaId,
                    id,
                    grupo.getId(),
                    item.ordem() != null ? item.ordem() : 0,
                    item.minSelecaoOverride(),
                    item.maxSelecaoOverride()
            );
            produtoModificadorGrupoRepository.save(vinculo);
        }

        incrementarVersaoEPublicarEventoCatalogo(lojaId, "PRODUTO_ALTERADO", List.of(id));
        SnapshotDisponibilidadeLoja snapshot = calcularSnapshotDisponibilidade(lojaId);
        return toProdutoDetalheResponse(lojaId, produto, snapshot);
    }

    @Transactional
    public ProdutoDetalheResponse vincularInsumosProduto(UUID id, VinculoInsumosRequest req) {
        UUID lojaId = tenantContext.requireCurrentLojaId();
        Produto produto = buscarProdutoObrigatorio(lojaId, id);
        if (req.versao() != null) {
            validarVersaoObrigatoria(req.versao(), produto.getVersao(), produto.isAtivo() ? "ATIVO" : "INATIVO");
        }

        validarInsumosPertencemALoja(lojaId, req.insumoIds());
        produto.setInsumoIds(new HashSet<>(req.insumoIds()));
        Produto salvo = produtoRepository.saveAndFlush(produto);

        incrementarVersaoEPublicarEventoCatalogo(lojaId, "PRODUTO_ALTERADO", List.of(id));
        SnapshotDisponibilidadeLoja snapshot = calcularSnapshotDisponibilidade(lojaId);
        return toProdutoDetalheResponse(lojaId, salvo, snapshot);
    }

    @Transactional
    public ProdutoDetalheResponse definirSlotsCombo(UUID produtoComboId, ComboSlotsRequest req) {
        UUID lojaId = tenantContext.requireCurrentLojaId();
        Produto combo = buscarProdutoObrigatorio(lojaId, produtoComboId);
        if (req.versao() != null) {
            validarVersaoObrigatoria(req.versao(), combo.getVersao(), combo.isAtivo() ? "ATIVO" : "INATIVO");
        }

        if (combo.getTipo() != TipoProduto.COMBO) {
            throw new OrderFlowException(
                    CodigoErro.PRODUTO_NAO_E_COMBO,
                    "Apenas produtos do tipo COMBO podem possuir slots configuráveis."
            );
        }

        // Remove slots e opções anteriores do combo
        List<ComboSlot> slotsAntigos = comboSlotRepository.findByLojaIdAndProdutoComboIdOrderByOrdemAsc(lojaId, produtoComboId);
        if (!slotsAntigos.isEmpty()) {
            List<UUID> slotIdsAntigos = slotsAntigos.stream().map(ComboSlot::getId).toList();
            comboSlotOpcaoRepository.deleteByLojaIdAndSlotIds(lojaId, slotIdsAntigos);
            comboSlotRepository.deleteByLojaIdAndProdutoComboId(lojaId, produtoComboId);
        }

        // Valida todos os produtos referenciados nas opções (proíbe combo aninhado)
        Set<UUID> todosProdutosOpcaoIds = req.slots().stream()
                .flatMap(s -> s.opcoes().stream())
                .map(ComboSlotOpcaoRequest::produtoId)
                .collect(Collectors.toSet());

        Map<UUID, Produto> produtosOpcaoMap = produtoRepository.findByLojaIdAndIdIn(lojaId, todosProdutosOpcaoIds)
                .stream()
                .collect(Collectors.toMap(Produto::getId, p -> p));

        for (UUID prodOpcaoId : todosProdutosOpcaoIds) {
            Produto prodOpcao = produtosOpcaoMap.get(prodOpcaoId);
            if (prodOpcao == null || prodOpcao.isArquivado()) {
                throw new OrderFlowException(
                        CodigoErro.RECURSO_NAO_ENCONTRADO,
                        "Produto de opção do slot não encontrado na loja: " + prodOpcaoId
                );
            }
            if (prodOpcao.getTipo() == TipoProduto.COMBO) {
                throw new OrderFlowException(
                        CodigoErro.COMBO_ANINHADO,
                        "Combos aninhados não são permitidos: o produto '" + prodOpcao.getNome() + "' é do tipo COMBO."
                );
            }
        }

        int idxSlot = 0;
        for (ComboSlotItemRequest slotReq : req.slots()) {
            int min = slotReq.resolveMinSelecao();
            int max = slotReq.resolveMaxSelecao();
            validarLimitesSelecao(min, max);

            ComboSlot novoSlot = comboSlotRepository.save(new ComboSlot(
                    slotReq.id(),
                    lojaId,
                    produtoComboId,
                    slotReq.nome(),
                    min,
                    max,
                    slotReq.ordem() != null ? slotReq.ordem() : idxSlot++
            ));

            int idxOpcao = 0;
            for (ComboSlotOpcaoRequest opReq : slotReq.opcoes()) {
                long acrescimo = opReq.acrescimoCentavos() != null ? opReq.acrescimoCentavos() : 0L;
                if (acrescimo < 0) {
                    throw new OrderFlowException(CodigoErro.PRECO_INVALIDO, "Acréscimo de opção de combo não pode ser negativo.");
                }
                comboSlotOpcaoRepository.save(new ComboSlotOpcao(
                        lojaId,
                        novoSlot.getId(),
                        opReq.produtoId(),
                        acrescimo,
                        Boolean.TRUE.equals(opReq.padrao()),
                        opReq.ordem() != null ? opReq.ordem() : idxOpcao++
                ));
            }
        }

        incrementarVersaoEPublicarEventoCatalogo(lojaId, "PRODUTO_ALTERADO", List.of(produtoComboId));
        SnapshotDisponibilidadeLoja snapshot = calcularSnapshotDisponibilidade(lojaId);
        return toProdutoDetalheResponse(lojaId, combo, snapshot);
    }

    @Transactional
    public void arquivarProduto(UUID id) {
        UUID lojaId = tenantContext.requireCurrentLojaId();
        Produto produto = buscarProdutoObrigatorio(lojaId, id);
        produto.arquivar(Instant.now());
        produtoRepository.save(produto);
        incrementarVersaoEPublicarEventoCatalogo(lojaId, "PRODUTO_ALTERADO", List.of(id));
    }

    @Transactional(readOnly = true)
    public List<HistoricoPrecoResponse> historicoPrecosProduto(UUID id) {
        UUID lojaId = tenantContext.requireCurrentLojaId();
        buscarProdutoObrigatorio(lojaId, id);
        return historicoPrecoRepository.findByLojaIdAndEntidadeTipoAndEntidadeIdOrderByVigenteDesdeDesc(lojaId, "PRODUTO", id)
                .stream()
                .map(this::toHistoricoPrecoResponse)
                .toList();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 5. PROJEÇÃO COMPLETA DE LEITURA DO CATÁLOGO (GET /api/v1/catalogo + ETag)
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public long obterVersaoAtualCatalogo() {
        UUID lojaId = tenantContext.requireCurrentLojaId();
        return catalogoVersaoRepository.findById(lojaId)
                .map(CatalogoVersao::getVersao)
                .orElse(1L);
    }

    @Transactional(readOnly = true)
    public CatalogoResponse obterCatalogoProjecao() {
        UUID lojaId = tenantContext.requireCurrentLojaId();
        long versao = obterVersaoAtualCatalogo();
        SnapshotDisponibilidadeLoja snapshot = calcularSnapshotDisponibilidade(lojaId);

        List<Categoria> categoriasAtivas = categoriaRepository.findByLojaIdAndArquivadoEmIsNullOrderByOrdemExibicaoAscNomeAsc(lojaId)
                .stream()
                .filter(Categoria::isAtivo)
                .toList();

        Map<UUID, List<Categoria>> subPorPai = categoriasAtivas.stream()
                .filter(c -> c.getCategoriaPaiId() != null)
                .collect(Collectors.groupingBy(Categoria::getCategoriaPaiId, LinkedHashMap::new, Collectors.toList()));

        Map<UUID, List<Produto>> produtosPorCategoria = snapshot.produtosPorId().values().stream()
                .filter(p -> p.isAtivo() && !p.isArquivado())
                .sorted(Comparator.comparingInt(Produto::getOrdem).thenComparing(Produto::getNome))
                .collect(Collectors.groupingBy(Produto::getCategoriaId, LinkedHashMap::new, Collectors.toList()));

        List<CatalogoCategoriaNode> raizes = categoriasAtivas.stream()
                .filter(c -> c.getCategoriaPaiId() == null)
                .map(raiz -> montarCategoriaCatalogoNode(
                        raiz,
                        subPorPai.getOrDefault(raiz.getId(), List.of()),
                        produtosPorCategoria,
                        snapshot
                ))
                .toList();

        return new CatalogoResponse(versao, Instant.now(), raizes);
    }

    private CatalogoCategoriaNode montarCategoriaCatalogoNode(
            Categoria cat,
            List<Categoria> filhas,
            Map<UUID, List<Produto>> produtosPorCategoria,
            SnapshotDisponibilidadeLoja snapshot
    ) {
        List<CatalogoProdutoNode> prods = produtosPorCategoria.getOrDefault(cat.getId(), List.of())
                .stream()
                .map(p -> montarProdutoCatalogoNode(p, snapshot))
                .toList();

        List<CatalogoCategoriaNode> subNodes = filhas.stream()
                .map(sub -> montarCategoriaCatalogoNode(sub, List.of(), produtosPorCategoria, snapshot))
                .toList();

        return new CatalogoCategoriaNode(cat.getId(), cat.getNome(), cat.getOrdemExibicao(), prods, subNodes);
    }

    private CatalogoProdutoNode montarProdutoCatalogoNode(Produto p, SnapshotDisponibilidadeLoja snapshot) {
        List<ProdutoModificadorGrupo> vinculos = snapshot.vinculosPorProduto().getOrDefault(p.getId(), List.of());
        List<CatalogoGrupoNode> grupos = new ArrayList<>();
        for (ProdutoModificadorGrupo v : vinculos) {
            GrupoModificador g = snapshot.gruposPorId().get(v.getGrupoId());
            if (g != null && g.isAtivo() && !g.isArquivado()) {
                List<ModificadorResponse> opcoes = snapshot.modificadoresPorGrupo().getOrDefault(g.getId(), List.of())
                        .stream()
                        .filter(m -> m.isAtivo() && !m.isArquivado())
                        .map(m -> toModificadorResponse(m, snapshot.modificadorDisponivel(m.getId()), snapshot.motivoModificador(m)))
                        .toList();
                grupos.add(new CatalogoGrupoNode(
                        g.getId(),
                        g.getNome(),
                        v.calcularMinSelecaoEfetiva(g),
                        v.calcularMaxSelecaoEfetiva(g),
                        g.isPermiteRepeticao(),
                        opcoes
                ));
            }
        }

        List<ComboSlotResponse> slots = p.getTipo() == TipoProduto.COMBO
                ? montarComboSlotsResponse(p.getId(), snapshot)
                : List.of();

        return new CatalogoProdutoNode(
                p.getId(),
                p.getNome(),
                p.getDescricao(),
                p.getImagemUrl(),
                p.getTipo(),
                p.getPrecoBaseCentavos(),
                snapshot.produtoDisponivel(p.getId()),
                snapshot.motivoProduto(p),
                p.getOrdem(),
                grupos,
                slots
        );
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 6. MOTOR DETERMINÍSTICO DE DISPONIBILIDADE EM CASCATA
    // ═══════════════════════════════════════════════════════════════════════════

    private SnapshotDisponibilidadeLoja calcularSnapshotDisponibilidade(UUID lojaId) {
        Map<UUID, Insumo> insumosPorId = insumoRepository.findByLojaIdOrderByNomeAsc(lojaId).stream()
                .collect(Collectors.toMap(Insumo::getId, i -> i));

        Map<UUID, GrupoModificador> gruposPorId = grupoModificadorRepository.findByLojaIdAndArquivadoEmIsNullOrderByOrdemAscNomeAsc(lojaId)
                .stream()
                .collect(Collectors.toMap(GrupoModificador::getId, g -> g, (a, b) -> a, LinkedHashMap::new));

        List<OpcaoModificador> todosMods = opcaoModificadorRepository.findByLojaIdAndArquivadoEmIsNull(lojaId);
        Map<UUID, OpcaoModificador> modsPorId = todosMods.stream()
                .collect(Collectors.toMap(OpcaoModificador::getId, m -> m));
        Map<UUID, List<OpcaoModificador>> modsPorGrupo = todosMods.stream()
                .sorted(Comparator.comparingInt(OpcaoModificador::getOrdem).thenComparing(OpcaoModificador::getNome))
                .collect(Collectors.groupingBy(OpcaoModificador::getGrupoId, LinkedHashMap::new, Collectors.toList()));

        List<Produto> todosProdutos = produtoRepository.findByLojaIdAndArquivadoEmIsNullOrderByOrdemAscNomeAsc(lojaId);
        Map<UUID, Produto> produtosPorId = todosProdutos.stream()
                .collect(Collectors.toMap(Produto::getId, p -> p, (a, b) -> a, LinkedHashMap::new));

        List<ProdutoModificadorGrupo> vinculos = produtoModificadorGrupoRepository.findByLojaId(lojaId);
        Map<UUID, List<ProdutoModificadorGrupo>> vinculosPorProduto = vinculos.stream()
                .sorted(Comparator.comparingInt(ProdutoModificadorGrupo::getOrdem))
                .collect(Collectors.groupingBy(ProdutoModificadorGrupo::getProdutoId, LinkedHashMap::new, Collectors.toList()));

        List<ComboSlot> todosSlots = comboSlotRepository.findByLojaIdOrderByOrdemAsc(lojaId);
        Map<UUID, List<ComboSlot>> slotsPorCombo = todosSlots.stream()
                .collect(Collectors.groupingBy(ComboSlot::getProdutoComboId, LinkedHashMap::new, Collectors.toList()));

        List<ComboSlotOpcao> todasOpcoesCombo = comboSlotOpcaoRepository.findByLojaIdOrderByOrdemAsc(lojaId);
        Map<UUID, List<ComboSlotOpcao>> opcoesPorSlot = todasOpcoesCombo.stream()
                .collect(Collectors.groupingBy(ComboSlotOpcao::getSlotId, LinkedHashMap::new, Collectors.toList()));

        // 1. Disponibilidade de cada opção de modificador
        Map<UUID, Boolean> modDisponivel = new HashMap<>();
        for (OpcaoModificador m : todosMods) {
            boolean insumosOk = m.getInsumoIds().stream()
                    .map(insumosPorId::get)
                    .allMatch(i -> i != null && i.isDisponivel());
            modDisponivel.put(m.getId(), m.isDisponivelProprio() && insumosOk);
        }

        // 2. Disponibilidade de produtos SIMPLES
        Map<UUID, Boolean> prodDisponivel = new HashMap<>();
        for (Produto p : todosProdutos) {
            if (p.getTipo() == TipoProduto.SIMPLES) {
                prodDisponivel.put(p.getId(), calcularDisponibilidadeProdutoSimples(
                        p, insumosPorId, gruposPorId, modsPorGrupo, vinculosPorProduto, modDisponivel
                ));
            }
        }

        // 3. Disponibilidade de produtos COMBO (depende dos slots e dos produtos simples escolhíveis)
        for (Produto p : todosProdutos) {
            if (p.getTipo() == TipoProduto.COMBO) {
                boolean simplesBaseOk = calcularDisponibilidadeProdutoSimples(
                        p, insumosPorId, gruposPorId, modsPorGrupo, vinculosPorProduto, modDisponivel
                );
                List<ComboSlot> slots = slotsPorCombo.getOrDefault(p.getId(), List.of());
                boolean todosSlotsOk = true;
                for (ComboSlot slot : slots) {
                    if (slot.getMinSelecao() >= 1) {
                        List<ComboSlotOpcao> opcoes = opcoesPorSlot.getOrDefault(slot.getId(), List.of());
                        long opcoesDisp = opcoes.stream()
                                .filter(o -> Boolean.TRUE.equals(prodDisponivel.get(o.getProdutoId())))
                                .count();
                        if (opcoesDisp < 1) {
                            todosSlotsOk = false;
                            break;
                        }
                    }
                }
                prodDisponivel.put(p.getId(), simplesBaseOk && todosSlotsOk);
            }
        }

        return new SnapshotDisponibilidadeLoja(
                insumosPorId,
                gruposPorId,
                modsPorId,
                modsPorGrupo,
                produtosPorId,
                vinculos,
                vinculosPorProduto,
                slotsPorCombo,
                opcoesPorSlot,
                modDisponivel,
                prodDisponivel
        );
    }

    private boolean calcularDisponibilidadeProdutoSimples(
            Produto p,
            Map<UUID, Insumo> insumosPorId,
            Map<UUID, GrupoModificador> gruposPorId,
            Map<UUID, List<OpcaoModificador>> modsPorGrupo,
            Map<UUID, List<ProdutoModificadorGrupo>> vinculosPorProduto,
            Map<UUID, Boolean> modDisponivel
    ) {
        if (!p.isDisponivelProprio()) {
            return false;
        }
        boolean insumosDiretosOk = p.getInsumoIds().stream()
                .map(insumosPorId::get)
                .allMatch(i -> i != null && i.isDisponivel());
        if (!insumosDiretosOk) {
            return false;
        }

        // Regra §2.2: se um grupo obrigatório vinculado ao produto ficou sem opções disponíveis suficientes, bloqueia o produto
        List<ProdutoModificadorGrupo> vinculos = vinculosPorProduto.getOrDefault(p.getId(), List.of());
        for (ProdutoModificadorGrupo v : vinculos) {
            GrupoModificador grupo = gruposPorId.get(v.getGrupoId());
            if (grupo == null || !grupo.isAtivo() || grupo.isArquivado()) {
                continue;
            }
            int minEfetivo = v.calcularMinSelecaoEfetiva(grupo);
            if (minEfetivo >= 1) {
                List<OpcaoModificador> opcoes = modsPorGrupo.getOrDefault(grupo.getId(), List.of());
                long disponiveisNoGrupo = opcoes.stream()
                        .filter(m -> Boolean.TRUE.equals(modDisponivel.get(m.getId())))
                        .count();
                if (disponiveisNoGrupo == 0 || (!grupo.isPermiteRepeticao() && disponiveisNoGrupo < minEfetivo)) {
                    return false;
                }
            }
        }
        return true;
    }

    private record SnapshotDisponibilidadeLoja(
            Map<UUID, Insumo> insumosPorId,
            Map<UUID, GrupoModificador> gruposPorId,
            Map<UUID, OpcaoModificador> modificadoresPorId,
            Map<UUID, List<OpcaoModificador>> modificadoresPorGrupo,
            Map<UUID, Produto> produtosPorId,
            List<ProdutoModificadorGrupo> vinculosGrupos,
            Map<UUID, List<ProdutoModificadorGrupo>> vinculosPorProduto,
            Map<UUID, List<ComboSlot>> slotsPorCombo,
            Map<UUID, List<ComboSlotOpcao>> opcoesPorSlot,
            Map<UUID, Boolean> modDisponivelMap,
            Map<UUID, Boolean> prodDisponivelMap
    ) {
        boolean modificadorDisponivel(UUID id) {
            return Boolean.TRUE.equals(modDisponivelMap.get(id));
        }

        boolean produtoDisponivel(UUID id) {
            return Boolean.TRUE.equals(prodDisponivelMap.get(id));
        }

        String motivoModificador(OpcaoModificador m) {
            if (m.isPausado()) {
                return m.getMotivoPausa() != null ? m.getMotivoPausa() : "Modificador pausado";
            }
            for (UUID insId : m.getInsumoIds()) {
                Insumo i = insumosPorId.get(insId);
                if (i != null && !i.isDisponivel()) {
                    return "Insumo esgotado: " + i.getNome();
                }
            }
            return null;
        }

        String motivoProduto(Produto p) {
            if (p.isPausado()) {
                return p.getMotivoPausa() != null ? p.getMotivoPausa() : "Produto pausado";
            }
            for (UUID insId : p.getInsumoIds()) {
                Insumo i = insumosPorId.get(insId);
                if (i != null && !i.isDisponivel()) {
                    return "Insumo esgotado: " + i.getNome();
                }
            }
            if (!produtoDisponivel(p.getId())) {
                return "Complemento obrigatório esgotado";
            }
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 7. HELPERS DE MAPEAMENTO, VALIDAÇÃO E PUBLICAÇÃO STOMP
    // ═══════════════════════════════════════════════════════════════════════════

    private long incrementarVersaoCatalogo(UUID lojaId) {
        CatalogoVersao cv = catalogoVersaoRepository.findById(lojaId)
                .orElseGet(() -> new CatalogoVersao(lojaId, 0L));
        long novaVersao = cv.incrementar();
        catalogoVersaoRepository.save(cv);
        return novaVersao;
    }

    private long incrementarVersaoEPublicarEventoCatalogo(UUID lojaId, String tipoEvento, List<UUID> ids) {
        long versao = incrementarVersaoCatalogo(lojaId);
        CatalogoEventoStomp payload = new CatalogoEventoStomp(versao, tipoEvento, ids, null);
        messagingTemplate.convertAndSend("/topic/lojas/" + lojaId + "/catalogo", payload);
        return versao;
    }

    private void publicarEventoDisponibilidadeStomp(
            UUID lojaId,
            long versao,
            List<UUID> ids,
            List<DisponibilidadeItemEvento> disponibilidade
    ) {
        CatalogoEventoStomp payload = new CatalogoEventoStomp(
                versao,
                "DISPONIBILIDADE_ALTERADA",
                ids,
                disponibilidade
        );
        messagingTemplate.convertAndSend("/topic/lojas/" + lojaId + "/disponibilidade", payload);
    }

    private void validarLimitesSelecao(Integer min, Integer max) {
        if (min == null || max == null || min < 0 || max < 1 || max < min) {
            throw new OrderFlowException(
                    CodigoErro.LIMITES_SELECAO_INVALIDOS,
                    "Limites de seleção inválidos: exige minSelecao >= 0, maxSelecao >= 1 e maxSelecao >= minSelecao."
            );
        }
    }

    private void validarPrecoModificador(TipoPreco tipoPreco, long valor) {
        if (tipoPreco == TipoPreco.FIXO && valor < 0) {
            throw new OrderFlowException(CodigoErro.PRECO_INVALIDO, "Adicional de preço fixo não pode ser negativo.");
        }
        if (tipoPreco == TipoPreco.PERCENTUAL && valor < -10_000L) {
            throw new OrderFlowException(CodigoErro.PRECO_INVALIDO, "Desconto percentual não pode exceder 100% (-10000 basis points).");
        }
    }

    private void validarInsumosPertencemALoja(UUID lojaId, List<UUID> insumoIds) {
        if (insumoIds == null || insumoIds.isEmpty()) {
            return;
        }
        Set<UUID> unicos = new HashSet<>(insumoIds);
        List<Insumo> encontrados = insumoRepository.findByLojaIdAndIdIn(lojaId, unicos);
        if (encontrados.size() != unicos.size()) {
            throw new OrderFlowException(CodigoErro.RECURSO_NAO_ENCONTRADO, "Um ou mais insumos informados não pertencem a esta loja.");
        }
    }

    private void validarVersaoObrigatoria(Integer versaoEnviada, Integer versaoAtual, String statusAtual) {
        if (versaoEnviada == null) {
            throw new OrderFlowException(CodigoErro.VERSAO_AUSENTE, "O campo 'versao' é obrigatório para atualizar este recurso.");
        }
        if (!versaoEnviada.equals(versaoAtual)) {
            throw OrderFlowException.conflitoEstado(
                    CodigoErro.VERSAO_CONFLITANTE,
                    "Versão enviada (" + versaoEnviada + ") diverge da versão atual (" + versaoAtual + ").",
                    statusAtual,
                    versaoAtual
            );
        }
    }

    private Categoria buscarCategoriaObrigatoria(UUID lojaId, UUID id) {
        return categoriaRepository.findByIdAndLojaId(id, lojaId)
                .orElseThrow(() -> new OrderFlowException(CodigoErro.RECURSO_NAO_ENCONTRADO, "Categoria não encontrada: " + id));
    }

    private Insumo buscarInsumoObrigatorio(UUID lojaId, UUID id) {
        return insumoRepository.findByIdAndLojaId(id, lojaId)
                .orElseThrow(() -> new OrderFlowException(CodigoErro.RECURSO_NAO_ENCONTRADO, "Insumo não encontrado: " + id));
    }

    private GrupoModificador buscarGrupoObrigatorio(UUID lojaId, UUID id) {
        return grupoModificadorRepository.findByIdAndLojaId(id, lojaId)
                .filter(g -> !g.isArquivado())
                .orElseThrow(() -> new OrderFlowException(CodigoErro.RECURSO_NAO_ENCONTRADO, "Grupo de modificadores não encontrado: " + id));
    }

    private OpcaoModificador buscarModificadorObrigatorio(UUID lojaId, UUID id) {
        return opcaoModificadorRepository.findByIdAndLojaId(id, lojaId)
                .filter(m -> !m.isArquivado())
                .orElseThrow(() -> new OrderFlowException(CodigoErro.RECURSO_NAO_ENCONTRADO, "Modificador não encontrado: " + id));
    }

    private Produto buscarProdutoObrigatorio(UUID lojaId, UUID id) {
        return produtoRepository.findByIdAndLojaId(id, lojaId)
                .filter(p -> !p.isArquivado())
                .orElseThrow(() -> new OrderFlowException(CodigoErro.RECURSO_NAO_ENCONTRADO, "Produto não encontrado: " + id));
    }

    private CategoriaResponse toCategoriaResponse(Categoria c, List<Categoria> filhas) {
        List<CategoriaResponse> subResponses = filhas.stream()
                .map(sub -> toCategoriaResponse(sub, List.of()))
                .toList();
        return new CategoriaResponse(
                c.getId(),
                c.getCategoriaPaiId(),
                c.getCategoriaPaiId(),
                c.getNome(),
                c.getSlug(),
                c.getOrdemExibicao(),
                c.getOrdemExibicao(),
                c.isAtivo(),
                c.isArquivada(),
                c.getEstacaoPreparoPadrao(),
                c.getArquivadoEm(),
                c.getVersao(),
                subResponses,
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }

    private InsumoResponse toInsumoResponse(Insumo i) {
        return new InsumoResponse(
                i.getId(),
                i.getNome(),
                i.getUnidadeMedida(),
                i.getUnidadeMedida(),
                i.getQuantidadeAtual(),
                i.getEstoqueMinimo(),
                i.isDisponivel(),
                i.getMotivoIndisponibilidade(),
                i.getVersao()
        );
    }

    private ModificadorResponse toModificadorResponse(OpcaoModificador m, boolean disponivelEfetivo, String motivo) {
        return new ModificadorResponse(
                m.getId(),
                m.getGrupoId(),
                m.getNome(),
                m.getTipoPreco(),
                m.getValorPreco(),
                m.getPrecoAdicionalCentavos(),
                m.isPadrao(),
                m.getMaxQuantidade(),
                m.getOrdem(),
                m.isAtivo(),
                m.isPausado(),
                disponivelEfetivo,
                motivo,
                m.getInsumoIds().stream().sorted().toList(),
                m.getVersao()
        );
    }

    private GrupoModificadorResponse toGrupoModificadorResponse(
            UUID lojaId,
            GrupoModificador g,
            SnapshotDisponibilidadeLoja snapshot
    ) {
        List<ModificadorResponse> mods = snapshot.modificadoresPorGrupo().getOrDefault(g.getId(), List.of())
                .stream()
                .map(m -> toModificadorResponse(m, snapshot.modificadorDisponivel(m.getId()), snapshot.motivoModificador(m)))
                .toList();
        long vinculados = produtoModificadorGrupoRepository.countActiveProductsUsingGrupo(lojaId, g.getId());
        return new GrupoModificadorResponse(
                g.getId(),
                g.getNome(),
                g.getNomeInterno(),
                g.getMinSelecao(),
                g.getMaxSelecao(),
                g.isPermiteRepeticao(),
                g.getOrdem(),
                g.isAtivo(),
                g.getVersao(),
                vinculados,
                mods
        );
    }

    private ProdutoResumoResponse toProdutoResumoResponse(Produto p, boolean disponivelEfetivo) {
        return new ProdutoResumoResponse(
                p.getId(),
                p.getCategoriaId(),
                p.getSku(),
                p.getNome(),
                p.getTipo(),
                p.getPrecoBaseCentavos(),
                p.getImagemUrl(),
                p.isAtivo(),
                p.isPausado(),
                disponivelEfetivo,
                p.getVersao()
        );
    }

    private ProdutoDetalheResponse toProdutoDetalheResponse(
            UUID lojaId,
            Produto p,
            SnapshotDisponibilidadeLoja snapshot
    ) {
        EstacaoPreparo efetiva = p.getEstacaoPreparo();
        if (efetiva == null) {
            efetiva = categoriaRepository.findByIdAndLojaId(p.getCategoriaId(), lojaId)
                    .map(Categoria::getEstacaoPreparoPadrao)
                    .orElse(EstacaoPreparo.COZINHA);
        }

        List<VinculoGrupoResponse> gruposVinculados = snapshot.vinculosPorProduto().getOrDefault(p.getId(), List.of())
                .stream()
                .map(v -> {
                    GrupoModificador g = snapshot.gruposPorId().get(v.getGrupoId());
                    if (g == null) {
                        return null;
                    }
                    return new VinculoGrupoResponse(
                            toGrupoModificadorResponse(lojaId, g, snapshot),
                            v.getOrdem(),
                            v.getMinSelecaoOverride(),
                            v.getMaxSelecaoOverride(),
                            v.calcularMinSelecaoEfetiva(g),
                            v.calcularMaxSelecaoEfetiva(g)
                    );
                })
                .filter(Objects::nonNull)
                .toList();

        List<InsumoResponse> insumos = p.getInsumoIds().stream()
                .map(snapshot.insumosPorId()::get)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(Insumo::getNome))
                .map(this::toInsumoResponse)
                .toList();

        List<ComboSlotResponse> slots = p.getTipo() == TipoProduto.COMBO
                ? montarComboSlotsResponse(p.getId(), snapshot)
                : List.of();

        return new ProdutoDetalheResponse(
                p.getId(),
                p.getCategoriaId(),
                p.getSku(),
                p.getNome(),
                p.getDescricao(),
                p.getImagemUrl(),
                p.getTipo(),
                p.getPrecoBaseCentavos(),
                p.getEstacaoPreparo(),
                efetiva,
                p.isAtivo(),
                p.isPausado(),
                snapshot.produtoDisponivel(p.getId()),
                p.getMotivoPausa(),
                snapshot.motivoProduto(p),
                p.getOrdem(),
                p.getVersao(),
                gruposVinculados,
                insumos,
                slots,
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }

    private List<ComboSlotResponse> montarComboSlotsResponse(UUID produtoComboId, SnapshotDisponibilidadeLoja snapshot) {
        List<ComboSlot> slots = snapshot.slotsPorCombo().getOrDefault(produtoComboId, List.of());
        return slots.stream().map(slot -> {
            List<ComboSlotOpcaoResponse> opcoes = snapshot.opcoesPorSlot().getOrDefault(slot.getId(), List.of())
                    .stream()
                    .map(o -> {
                        Produto prodFilho = snapshot.produtosPorId().get(o.getProdutoId());
                        String nomeFilho = prodFilho != null ? prodFilho.getNome() : "Produto";
                        return new ComboSlotOpcaoResponse(
                                o.getProdutoId(),
                                nomeFilho,
                                o.getAcrescimoCentavos(),
                                o.isPadrao(),
                                snapshot.produtoDisponivel(o.getProdutoId())
                        );
                    })
                    .toList();
            return new ComboSlotResponse(
                    slot.getId(),
                    slot.getNome(),
                    slot.getMinSelecao(),
                    slot.getMaxSelecao(),
                    slot.isObrigatorio(),
                    slot.getOrdem(),
                    opcoes
            );
        }).toList();
    }

    private HistoricoPrecoResponse toHistoricoPrecoResponse(HistoricoPreco h) {
        return new HistoricoPrecoResponse(
                h.getId(),
                h.getTipoPrecoAnterior(),
                h.getValorAnterior(),
                h.getTipoPrecoNovo(),
                h.getValorNovo(),
                h.getMotivo(),
                h.getVigenteDesde()
        );
    }
}
