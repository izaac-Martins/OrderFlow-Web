import React, { useCallback, useEffect, useState } from 'react';
import {
  Layers,
  Plus,
  Trash2,
  Sliders,
  History,
  Boxes,
  AlertTriangle,
  CheckCircle2,
  XCircle,
  Tag,
} from 'lucide-react';
import { apiRequest } from '../lib/http';
import { parseApiError } from '../lib/errors';
import {
  centavosToInputReais,
  formatCentavos,
  formatDateTimeBr,
  parseReaisToCentavos,
} from '../lib/formatters';
import { stompManager } from '../lib/stompClient';
import { useAuth } from '../context/AuthContext';
import type {
  Categoria,
  EstacaoPreparo,
  GrupoModificador,
  HistoricoPreco,
  Insumo,
  Produto,
  TipoProduto,
} from '../types/api';

export const CatalogoPage: React.FC = () => {
  const { user, isManagerOrAdmin } = useAuth();
  const [categorias, setCategorias] = useState<Categoria[]>([]);
  const [produtos, setProdutos] = useState<Produto[]>([]);
  const [grupos, setGrupos] = useState<GrupoModificador[]>([]);
  const [insumos, setInsumos] = useState<Insumo[]>([]);
  const [loading, setLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  // Formulário Nova Categoria (Hierarquia máx. 2 níveis)
  const [catNome, setCatNome] = useState('');
  const [catSlug, setCatSlug] = useState('');
  const [catParentId, setCatParentId] = useState<string>('');
  const [catEstacao, setCatEstacao] = useState<EstacaoPreparo>('BAR');

  // Formulário Novo Produto
  const [prodCategoriaId, setProdCategoriaId] = useState('');
  const [prodSku, setProdSku] = useState('');
  const [prodNome, setProdNome] = useState('');
  const [prodDescricao, setProdDescricao] = useState('');
  const [prodTipo, setProdTipo] = useState<TipoProduto>('SIMPLES');
  const [prodPrecoReais, setProdPrecoReais] = useState('16,00');
  const [prodEstacao, setProdEstacao] = useState<EstacaoPreparo>('BAR');

  // Produto em edição de vínculos (Grupos, Insumos, Combo Slots, Histórico de Preço)
  const [produtoSelecionado, setProdutoSelecionado] = useState<Produto | null>(
    null
  );
  const [historicoPrecos, setHistoricoPrecos] = useState<HistoricoPreco[]>([]);
  const [novoPrecoReais, setNovoPrecoReais] = useState('');
  const [motivoPreco, setMotivoPreco] = useState('');

  // Montador de Slots de Combo
  const [slotNome, setSlotNome] = useState('Escolha sua Bebida');
  const [slotMin, setSlotMin] = useState(1);
  const [slotMax, setSlotMax] = useState(1);
  const [slotOpcoesIds, setSlotOpcoesIds] = useState<string[]>([]);

  const carregarDados = useCallback(async () => {
    setLoading(true);
    try {
      const [cats, prods, grps, ins] = await Promise.all([
        apiRequest<Categoria[]>({ method: 'GET', url: '/categorias' }),
        apiRequest<Produto[]>({ method: 'GET', url: '/produtos' }),
        apiRequest<GrupoModificador[]>({
          method: 'GET',
          url: '/grupos-modificadores',
        }),
        apiRequest<Insumo[]>({ method: 'GET', url: '/catalogo/insumos' }),
      ]);
      setCategorias(cats);
      setProdutos(prods);
      setGrupos(grps);
      setInsumos(ins);
      if (!prodCategoriaId && cats.length > 0) {
        setProdCategoriaId(cats[0].id);
      }
    } catch (err) {
      setErrorMsg(parseApiError(err).message);
    } finally {
      setLoading(false);
    }
  }, [prodCategoriaId]);

  useEffect(() => {
    carregarDados();
  }, [carregarDados]);

  useEffect(() => {
    const lojaId = user?.lojaId || '019325f0-8a00-7000-8000-000000000001';
    return stompManager.subscribe(
      `/topic/lojas/${lojaId}/disponibilidade`,
      () => {
        carregarDados();
      }
    );
  }, [user?.lojaId, carregarDados]);

  const gerarSlugAutomatico = (texto: string) =>
    texto
      .toLowerCase()
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/(^-|-$)/g, '');

  const handleCriarCategoria = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg(null);
    setSuccessMsg(null);
    try {
      await apiRequest<Categoria>({
        method: 'POST',
        url: '/categorias',
        data: {
          parentId: catParentId || null,
          nome: catNome,
          slug: catSlug || gerarSlugAutomatico(catNome),
          ordemExibicao: categorias.length + 1,
          ativo: true,
          estacaoPreparoPadrao: catEstacao,
        },
      });
      setCatNome('');
      setCatSlug('');
      setCatParentId('');
      setSuccessMsg('Categoria cadastrada com sucesso.');
      await carregarDados();
    } catch (err) {
      setErrorMsg(parseApiError(err).message);
    }
  };

  const handleArquivarCategoria = async (cat: Categoria) => {
    setErrorMsg(null);
    setSuccessMsg(null);
    try {
      await apiRequest<void>({
        method: 'DELETE',
        url: `/categorias/${cat.id}`,
      });
      setSuccessMsg(`Categoria '${cat.nome}' arquivada.`);
      await carregarDados();
    } catch (err) {
      setErrorMsg(parseApiError(err).message);
    }
  };

  const handleCriarProduto = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg(null);
    setSuccessMsg(null);
    try {
      const precoCentavos = parseReaisToCentavos(prodPrecoReais);
      const criado = await apiRequest<Produto>({
        method: 'POST',
        url: '/produtos',
        data: {
          categoriaId: prodCategoriaId,
          sku: prodSku,
          nome: prodNome,
          descricao: prodDescricao || null,
          tipo: prodTipo,
          precoBaseCentavos: precoCentavos,
          estacaoPreparo: prodTipo === 'COMBO' ? 'NENHUMA' : prodEstacao,
          ativo: true,
          disponivel: true,
          ordem: produtos.length + 1,
        },
      });
      setProdSku('');
      setProdNome('');
      setProdDescricao('');
      setSuccessMsg(
        `Produto '${criado.nome}' (${criado.sku}) criado por ${formatCentavos(
          criado.precoBaseCentavos
        )}.`
      );
      await carregarDados();
    } catch (err) {
      setErrorMsg(parseApiError(err).message);
    }
  };

  const abrirConfiguracaoProduto = async (prod: Produto) => {
    setProdutoSelecionado(prod);
    setNovoPrecoReais(centavosToInputReais(prod.precoBaseCentavos));
    setMotivoPreco('');
    try {
      const hist = await apiRequest<HistoricoPreco[]>({
        method: 'GET',
        url: `/produtos/${prod.id}/historico-precos`,
      });
      setHistoricoPrecos(hist);
    } catch {
      setHistoricoPrecos([]);
    }
  };

  const handleAlterarPrecoComHistorico = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!produtoSelecionado) return;
    setErrorMsg(null);
    try {
      const atualizado = await apiRequest<Produto>({
        method: 'PATCH',
        url: `/produtos/${produtoSelecionado.id}/preco`,
        data: {
          versao: produtoSelecionado.versao,
          precoBaseCentavos: parseReaisToCentavos(novoPrecoReais),
          motivo: motivoPreco,
        },
      });
      setProdutoSelecionado(atualizado);
      setMotivoPreco('');
      const hist = await apiRequest<HistoricoPreco[]>({
        method: 'GET',
        url: `/produtos/${atualizado.id}/historico-precos`,
      });
      setHistoricoPrecos(hist);
      setSuccessMsg('Preço base atualizado e registrado no histórico imutável.');
      await carregarDados();
    } catch (err) {
      setErrorMsg(parseApiError(err).message);
    }
  };

  const handleToggleGrupoVinculado = async (grupoId: string) => {
    if (!produtoSelecionado) return;
    setErrorMsg(null);
    const atuais = produtoSelecionado.gruposModificadores.map(
      (v) => v.grupo.id
    );
    const novosIds = atuais.includes(grupoId)
      ? atuais.filter((id) => id !== grupoId)
      : [...atuais, grupoId];

    try {
      const atualizado = await apiRequest<Produto>({
        method: 'PUT',
        url: `/produtos/${produtoSelecionado.id}/grupos-modificadores`,
        data: {
          versao: produtoSelecionado.versao,
          grupos: novosIds.map((id, idx) => ({
            grupoId: id,
            ordem: idx + 1,
          })),
        },
      });
      setProdutoSelecionado(atualizado);
      await carregarDados();
    } catch (err) {
      setErrorMsg(parseApiError(err).message);
    }
  };

  const handleToggleInsumoVinculado = async (insumoId: string) => {
    if (!produtoSelecionado) return;
    setErrorMsg(null);
    const atuais = produtoSelecionado.insumoIds || [];
    const novosIds = atuais.includes(insumoId)
      ? atuais.filter((id) => id !== insumoId)
      : [...atuais, insumoId];

    try {
      const atualizado = await apiRequest<Produto>({
        method: 'PUT',
        url: `/produtos/${produtoSelecionado.id}/insumos`,
        data: {
          versao: produtoSelecionado.versao,
          insumoIds: novosIds,
        },
      });
      setProdutoSelecionado(atualizado);
      await carregarDados();
    } catch (err) {
      setErrorMsg(parseApiError(err).message);
    }
  };

  const handleAdicionarSlotCombo = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!produtoSelecionado) return;
    setErrorMsg(null);
    if (slotOpcoesIds.length === 0) {
      setErrorMsg('Selecione pelo menos 1 produto para compor as opções do slot.');
      return;
    }

    const slotsExistentes = (produtoSelecionado.comboSlots || []).map((s) => ({
      id: s.id,
      nome: s.nome,
      minSelecao: s.minSelecao,
      maxSelecao: s.maxSelecao,
      obrigatorio: s.minSelecao >= 1,
      ordem: s.ordem,
      opcoes: s.opcoes.map((o, idx) => ({
        produtoId: o.produtoId,
        acrescimoCentavos: o.acrescimoCentavos,
        padrao: o.padrao,
        ordem: idx + 1,
      })),
    }));

    const novoSlot = {
      nome: slotNome,
      minSelecao: slotMin,
      maxSelecao: slotMax,
      obrigatorio: slotMin >= 1,
      ordem: slotsExistentes.length + 1,
      opcoes: slotOpcoesIds.map((pid, idx) => ({
        produtoId: pid,
        acrescimoCentavos: 0,
        padrao: idx === 0,
        ordem: idx + 1,
      })),
    };

    try {
      const atualizado = await apiRequest<Produto>({
        method: 'PUT',
        url: `/produtos/${produtoSelecionado.id}/combo-slots`,
        data: {
          versao: produtoSelecionado.versao,
          slots: [...slotsExistentes, novoSlot],
        },
      });
      setProdutoSelecionado(atualizado);
      setSlotOpcoesIds([]);
      setSuccessMsg(`Slot '${slotNome}' adicionado ao combo.`);
      await carregarDados();
    } catch (err) {
      setErrorMsg(parseApiError(err).message);
    }
  };

  const categoriasRaiz = categorias.filter((c) => !c.parentId);

  return (
    <div className="space-y-6">
      {/* Cabeçalho */}
      <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2 text-amber-700 text-xs font-bold uppercase tracking-wider">
            <Layers className="w-4 h-4" />
            <span>Gestão de Cardápio Multi-Loja (`BIGINT` Centavos)</span>
          </div>
          <h1 className="text-2xl font-extrabold text-slate-900 mt-1">
            Categorias (2 Níveis), Produtos, Modificadores &amp; Combos
          </h1>
        </div>
      </div>

      {errorMsg && (
        <div className="rounded-2xl bg-rose-50 border border-rose-200 p-4 text-xs font-semibold text-rose-800 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <AlertTriangle className="w-4 h-4 shrink-0" />
            <span>{errorMsg}</span>
          </div>
          <button
            type="button"
            onClick={() => setErrorMsg(null)}
            className="underline"
          >
            Fechar
          </button>
        </div>
      )}

      {successMsg && (
        <div className="rounded-2xl bg-emerald-50 border border-emerald-200 p-4 text-xs font-semibold text-emerald-800 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <CheckCircle2 className="w-4 h-4 shrink-0" />
            <span>{successMsg}</span>
          </div>
          <button
            type="button"
            onClick={() => setSuccessMsg(null)}
            className="underline"
          >
            Fechar
          </button>
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Coluna Esquerda: Hierarquia de Categorias (Máx. 2 Níveis) */}
        <div className="lg:col-span-4 space-y-5">
          <div className="bg-white rounded-2xl border border-slate-200 p-5 shadow-sm space-y-4">
            <h2 className="text-base font-bold text-slate-900 flex items-center justify-between">
              <span>Árvore de Categorias</span>
              <span className="text-[11px] font-mono text-slate-500">
                Máx. 2 níveis
              </span>
            </h2>

            {loading ? (
              <p className="text-xs text-slate-500">Carregando categorias...</p>
            ) : (
              <div className="space-y-3">
                {categoriasRaiz.map((raiz) => {
                  const subcats = categorias.filter(
                    (c) => c.parentId === raiz.id
                  );
                  return (
                    <div
                      key={raiz.id}
                      className="rounded-xl border border-slate-200 p-3 bg-slate-50/60 space-y-2"
                    >
                      <div className="flex items-center justify-between">
                        <div>
                          <span className="text-xs font-bold text-slate-900">
                            {raiz.nome}
                          </span>
                          <span className="ml-2 text-[10px] font-mono text-slate-500">
                            /{raiz.slug} · {raiz.estacaoPreparoPadrao}
                          </span>
                        </div>
                        {isManagerOrAdmin && (
                          <button
                            type="button"
                            onClick={() => handleArquivarCategoria(raiz)}
                            className="p-1 text-slate-400 hover:text-rose-600 transition"
                            title="Arquivar categoria"
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                          </button>
                        )}
                      </div>

                      {subcats.length > 0 && (
                        <div className="pl-4 border-l-2 border-amber-400 space-y-1.5">
                          {subcats.map((sub) => (
                            <div
                              key={sub.id}
                              className="flex items-center justify-between bg-white rounded-lg px-2.5 py-1.5 border border-slate-200 text-xs"
                            >
                              <div>
                                <span className="font-semibold text-slate-800">
                                  ↳ {sub.nome}
                                </span>
                                <span className="ml-1.5 text-[10px] font-mono text-slate-400">
                                  /{sub.slug}
                                </span>
                              </div>
                              {isManagerOrAdmin && (
                                <button
                                  type="button"
                                  onClick={() => handleArquivarCategoria(sub)}
                                  className="text-slate-400 hover:text-rose-600"
                                >
                                  <Trash2 className="w-3.5 h-3.5" />
                                </button>
                              )}
                            </div>
                          ))}
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            )}

            {isManagerOrAdmin && (
              <form
                onSubmit={handleCriarCategoria}
                className="pt-4 border-t border-slate-200 space-y-3"
              >
                <span className="text-xs font-bold uppercase text-slate-500 block">
                  Nova Categoria / Subcategoria
                </span>
                <input
                  type="text"
                  required
                  value={catNome}
                  onChange={(e) => {
                    setCatNome(e.target.value);
                    setCatSlug(gerarSlugAutomatico(e.target.value));
                  }}
                  placeholder="Nome da categoria"
                  className="w-full px-3 py-2 rounded-xl border border-slate-300 text-xs"
                />
                <div className="grid grid-cols-2 gap-2">
                  <input
                    type="text"
                    required
                    value={catSlug}
                    onChange={(e) => setCatSlug(e.target.value)}
                    placeholder="slug-unico"
                    className="w-full px-3 py-2 rounded-xl border border-slate-300 text-xs font-mono"
                  />
                  <select
                    value={catEstacao}
                    onChange={(e) =>
                      setCatEstacao(e.target.value as EstacaoPreparo)
                    }
                    className="w-full px-3 py-2 rounded-xl border border-slate-300 text-xs"
                  >
                    <option value="BAR">Estação: BAR</option>
                    <option value="COZINHA">Estação: COZINHA</option>
                    <option value="NENHUMA">Estação: NENHUMA</option>
                  </select>
                </div>
                <select
                  value={catParentId}
                  onChange={(e) => setCatParentId(e.target.value)}
                  className="w-full px-3 py-2 rounded-xl border border-slate-300 text-xs"
                >
                  <option value="">Nível 1 — Categoria Raiz Principal</option>
                  {categorias.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.parentId
                        ? `[Nível 2 - Sub] ${c.nome} (Testar bloqueio 422 3º nível)`
                        : `Subcategoria de: ${c.nome} (Nível 2)`}
                    </option>
                  ))}
                </select>
                <button
                  type="submit"
                  className="w-full py-2 rounded-xl bg-slate-900 hover:bg-slate-800 text-white text-xs font-bold flex items-center justify-center gap-1.5"
                >
                  <Plus className="w-3.5 h-3.5" />
                  <span>Adicionar Categoria</span>
                </button>
              </form>
            )}
          </div>
        </div>

        {/* Coluna Direita: Produtos & Configuração de Combos/Modificadores */}
        <div className="lg:col-span-8 space-y-6">
          {/* Formulário Novo Produto */}
          {isManagerOrAdmin && (
            <form
              onSubmit={handleCriarProduto}
              className="bg-white rounded-2xl border border-slate-200 p-6 shadow-sm space-y-4"
            >
              <h2 className="text-base font-bold text-slate-900 flex items-center gap-2">
                <Plus className="w-4 h-4 text-amber-600" />
                <span>Cadastrar Novo Produto (`SIMPLES` ou `COMBO`)</span>
              </h2>
              <div className="grid grid-cols-1 sm:grid-cols-4 gap-3">
                <div>
                  <label className="block text-[11px] font-bold uppercase text-slate-500 mb-1">
                    SKU Único (Loja)
                  </label>
                  <input
                    type="text"
                    required
                    value={prodSku}
                    onChange={(e) => setProdSku(e.target.value.toUpperCase())}
                    placeholder="CAF-ESP-01"
                    className="w-full px-3 py-2 rounded-xl border border-slate-300 text-xs font-mono"
                  />
                </div>
                <div className="sm:col-span-2">
                  <label className="block text-[11px] font-bold uppercase text-slate-500 mb-1">
                    Nome Comercial
                  </label>
                  <input
                    type="text"
                    required
                    value={prodNome}
                    onChange={(e) => setProdNome(e.target.value)}
                    placeholder="Ex.: Flat White Duplo"
                    className="w-full px-3 py-2 rounded-xl border border-slate-300 text-xs"
                  />
                </div>
                <div>
                  <label className="block text-[11px] font-bold uppercase text-slate-500 mb-1">
                    Preço Base (R$)
                  </label>
                  <input
                    type="text"
                    required
                    value={prodPrecoReais}
                    onChange={(e) => setProdPrecoReais(e.target.value)}
                    placeholder="16,00"
                    className="w-full px-3 py-2 rounded-xl border border-slate-300 text-xs font-bold"
                  />
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                <div>
                  <label className="block text-[11px] font-bold uppercase text-slate-500 mb-1">
                    Categoria
                  </label>
                  <select
                    value={prodCategoriaId}
                    onChange={(e) => setProdCategoriaId(e.target.value)}
                    className="w-full px-3 py-2 rounded-xl border border-slate-300 text-xs"
                  >
                    {categorias.map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.parentId ? `↳ ${c.nome}` : c.nome}
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-[11px] font-bold uppercase text-slate-500 mb-1">
                    Tipo de Produto
                  </label>
                  <select
                    value={prodTipo}
                    onChange={(e) => setProdTipo(e.target.value as TipoProduto)}
                    className="w-full px-3 py-2 rounded-xl border border-slate-300 text-xs font-semibold"
                  >
                    <option value="SIMPLES">SIMPLES (Item Direto)</option>
                    <option value="COMBO">COMBO (Composto por Slots)</option>
                  </select>
                </div>
                <div>
                  <label className="block text-[11px] font-bold uppercase text-slate-500 mb-1">
                    Estação KDS
                  </label>
                  <select
                    value={prodEstacao}
                    disabled={prodTipo === 'COMBO'}
                    onChange={(e) =>
                      setProdEstacao(e.target.value as EstacaoPreparo)
                    }
                    className="w-full px-3 py-2 rounded-xl border border-slate-300 text-xs disabled:bg-slate-100"
                  >
                    <option value="BAR">BAR (Barista)</option>
                    <option value="COZINHA">COZINHA (Forno/Chapa)</option>
                    <option value="NENHUMA">NENHUMA</option>
                  </select>
                </div>
              </div>

              <div className="flex justify-end">
                <button
                  type="submit"
                  className="px-5 py-2.5 rounded-xl bg-amber-600 hover:bg-amber-700 text-white text-xs font-bold transition shadow-sm"
                >
                  Cadastrar Produto no Catálogo
                </button>
              </div>
            </form>
          )}

          {/* Lista de Produtos */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {produtos.map((p) => (
              <div
                key={p.id}
                className={`rounded-2xl border p-5 flex flex-col justify-between gap-4 shadow-sm transition ${
                  p.disponivel
                    ? 'bg-white border-slate-200'
                    : 'bg-rose-50/70 border-rose-300'
                }`}
              >
                <div className="space-y-2">
                  <div className="flex items-center justify-between gap-2">
                    <div className="flex items-center gap-1.5">
                      <span className="px-2 py-0.5 rounded bg-slate-100 text-slate-700 font-mono text-[10px] font-bold">
                        {p.sku}
                      </span>
                      <span
                        className={`px-2 py-0.5 rounded text-[10px] font-bold ${
                          p.tipo === 'COMBO'
                            ? 'bg-purple-100 text-purple-800'
                            : 'bg-sky-100 text-sky-800'
                        }`}
                      >
                        {p.tipo}
                      </span>
                      <span className="px-2 py-0.5 rounded bg-amber-100 text-amber-900 text-[10px] font-bold">
                        {p.estacaoPreparoEfetiva}
                      </span>
                    </div>
                    <span
                      className={`text-[11px] font-bold flex items-center gap-1 ${
                        p.disponivel ? 'text-emerald-700' : 'text-rose-700'
                      }`}
                    >
                      {p.disponivel ? (
                        <>
                          <CheckCircle2 className="w-3.5 h-3.5" />
                          <span>Disponível</span>
                        </>
                      ) : (
                        <>
                          <XCircle className="w-3.5 h-3.5" />
                          <span>Indisponível</span>
                        </>
                      )}
                    </span>
                  </div>

                  <div className="flex items-baseline justify-between gap-2">
                    <h3 className="text-base font-bold text-slate-900">
                      {p.nome}
                    </h3>
                    <span className="text-lg font-extrabold text-amber-700">
                      {formatCentavos(p.precoBaseCentavos)}
                    </span>
                  </div>

                  {p.descricao && (
                    <p className="text-xs text-slate-500">{p.descricao}</p>
                  )}

                  {!p.disponivel && p.motivoIndisponibilidade && (
                    <p className="text-xs font-semibold text-rose-700 bg-rose-100/80 px-2.5 py-1 rounded-lg">
                      {p.motivoIndisponibilidade}
                    </p>
                  )}
                </div>

                <div className="pt-3 border-t border-slate-100 flex items-center justify-between text-xs">
                  <span className="text-slate-500">
                    {p.tipo === 'COMBO'
                      ? `${p.comboSlots?.length || 0} slot(s) configurado(s)`
                      : `${p.gruposModificadores?.length || 0} grupo(s) · ${
                          p.insumoIds?.length || 0
                        } insumo(s)`}
                  </span>
                  <button
                    type="button"
                    onClick={() => abrirConfiguracaoProduto(p)}
                    className="px-3 py-1.5 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-800 font-bold flex items-center gap-1.5 transition"
                  >
                    <Sliders className="w-3.5 h-3.5" />
                    <span>Configurar / Preço</span>
                  </button>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Modal de Configuração Avançada do Produto (Grupos, Insumos, Combo Slots e Histórico de Preço) */}
      {produtoSelecionado && (
        <div className="fixed inset-0 z-40 bg-slate-950/60 backdrop-blur-sm flex items-center justify-center p-4 overflow-y-auto">
          <div className="bg-white rounded-3xl max-w-3xl w-full p-6 lg:p-8 space-y-6 shadow-2xl border border-slate-200 my-8 max-h-[90vh] overflow-y-auto">
            <div className="flex items-start justify-between gap-4 border-b border-slate-200 pb-4">
              <div>
                <span className="text-xs font-mono font-bold text-amber-700">
                  {produtoSelecionado.sku} · {produtoSelecionado.tipo} · v
                  {produtoSelecionado.versao}
                </span>
                <h2 className="text-xl font-extrabold text-slate-900">
                  {produtoSelecionado.nome}
                </h2>
              </div>
              <button
                type="button"
                onClick={() => setProdutoSelecionado(null)}
                className="px-3 py-1.5 rounded-xl bg-slate-100 hover:bg-slate-200 text-xs font-bold text-slate-700"
              >
                Fechar
              </button>
            </div>

            {/* 1. Reajuste de Preço & Histórico Imutável */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4 bg-slate-50 p-4 rounded-2xl border border-slate-200">
              <form
                onSubmit={handleAlterarPrecoComHistorico}
                className="space-y-3"
              >
                <div className="flex items-center gap-1.5 text-xs font-bold uppercase text-slate-700">
                  <Tag className="w-4 h-4 text-amber-600" />
                  <span>Alterar Preço Base (Gera Auditoria)</span>
                </div>
                <input
                  type="text"
                  required
                  value={novoPrecoReais}
                  onChange={(e) => setNovoPrecoReais(e.target.value)}
                  className="w-full px-3 py-2 rounded-xl border border-slate-300 text-sm font-bold"
                />
                <input
                  type="text"
                  required
                  minLength={5}
                  value={motivoPreco}
                  onChange={(e) => setMotivoPreco(e.target.value)}
                  placeholder="Motivo obrigatório (mín. 5 caracteres)"
                  className="w-full px-3 py-2 rounded-xl border border-slate-300 text-xs"
                />
                <button
                  type="submit"
                  className="w-full py-2 rounded-xl bg-amber-600 hover:bg-amber-700 text-white text-xs font-bold"
                >
                  Salvar Novo Preço
                </button>
              </form>

              <div className="space-y-2">
                <div className="flex items-center gap-1.5 text-xs font-bold uppercase text-slate-700">
                  <History className="w-4 h-4 text-slate-500" />
                  <span>Histórico Imutável (`historico_precos`)</span>
                </div>
                <div className="max-h-36 overflow-y-auto space-y-1.5">
                  {historicoPrecos.length === 0 ? (
                    <p className="text-xs text-slate-400">
                      Nenhuma alteração registrada ainda.
                    </p>
                  ) : (
                    historicoPrecos.map((h) => (
                      <div
                        key={h.id}
                        className="bg-white p-2.5 rounded-xl border border-slate-200 text-[11px]"
                      >
                        <div className="flex justify-between font-bold text-slate-800">
                          <span>
                            {formatCentavos(h.valorAnterior)} →{' '}
                            {formatCentavos(h.valorNovo)}
                          </span>
                          <span className="text-slate-400">
                            {formatDateTimeBr(h.vigenteDesde)}
                          </span>
                        </div>
                        <p className="text-slate-600 mt-0.5">{h.motivo}</p>
                      </div>
                    ))
                  )}
                </div>
              </div>
            </div>

            {/* 2. Se SIMPLES: Vínculo de Grupos de Modificadores e Insumos */}
            {produtoSelecionado.tipo === 'SIMPLES' && (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div className="border border-slate-200 rounded-2xl p-4 space-y-3">
                  <h3 className="text-xs font-bold uppercase text-slate-700">
                    Grupos de Modificadores Vinculados
                  </h3>
                  {grupos.map((g) => {
                    const ativo =
                      produtoSelecionado.gruposModificadores.some(
                        (v) => v.grupo.id === g.id
                      );
                    return (
                      <label
                        key={g.id}
                        className="flex items-center justify-between p-2.5 rounded-xl bg-slate-50 border border-slate-200 cursor-pointer text-xs"
                      >
                        <div>
                          <span className="font-bold text-slate-800 block">
                            {g.nome}
                          </span>
                          <span className="text-[10px] text-slate-500">
                            mín {g.minSelecao} / máx {g.maxSelecao} (
                            {g.modificadores.length} opções)
                          </span>
                        </div>
                        <input
                          type="checkbox"
                          checked={ativo}
                          onChange={() => handleToggleGrupoVinculado(g.id)}
                          className="w-4 h-4 accent-amber-600"
                        />
                      </label>
                    );
                  })}
                </div>

                <div className="border border-slate-200 rounded-2xl p-4 space-y-3">
                  <h3 className="text-xs font-bold uppercase text-slate-700">
                    Dependência de Insumos (Cascata 86)
                  </h3>
                  {insumos.map((ins) => {
                    const vinculado = (
                      produtoSelecionado.insumoIds || []
                    ).includes(ins.id);
                    return (
                      <label
                        key={ins.id}
                        className="flex items-center justify-between p-2.5 rounded-xl bg-slate-50 border border-slate-200 cursor-pointer text-xs"
                      >
                        <div>
                          <span className="font-bold text-slate-800 block">
                            {ins.nome}
                          </span>
                          <span
                            className={`text-[10px] font-semibold ${
                              ins.disponivel
                                ? 'text-emerald-600'
                                : 'text-rose-600'
                            }`}
                          >
                            {ins.disponivel ? 'Disponível' : 'Esgotado (86)'}
                          </span>
                        </div>
                        <input
                          type="checkbox"
                          checked={vinculado}
                          onChange={() => handleToggleInsumoVinculado(ins.id)}
                          className="w-4 h-4 accent-amber-600"
                        />
                      </label>
                    );
                  })}
                </div>
              </div>
            )}

            {/* 3. Se COMBO: Montagem de Slots & Bloqueio de Combo Aninhado */}
            {produtoSelecionado.tipo === 'COMBO' && (
              <div className="border border-purple-200 bg-purple-50/40 rounded-2xl p-5 space-y-4">
                <div className="flex items-center gap-2 text-purple-900 font-bold text-sm">
                  <Boxes className="w-4 h-4" />
                  <span>
                    Configuração de Slots do Combo (Valida 422 `COMBO_ANINHADO`)
                  </span>
                </div>

                <div className="space-y-2">
                  {(produtoSelecionado.comboSlots || []).map((s) => (
                    <div
                      key={s.id}
                      className="bg-white p-3 rounded-xl border border-purple-200 text-xs flex items-center justify-between"
                    >
                      <div>
                        <strong className="text-slate-900">{s.nome}</strong>{' '}
                        <span className="text-slate-500">
                          (mín {s.minSelecao} / máx {s.maxSelecao})
                        </span>
                        <div className="text-[11px] text-slate-600 mt-1">
                          Opções: {s.opcoes.map((o) => o.produtoNome).join(', ')}
                        </div>
                      </div>
                    </div>
                  ))}
                </div>

                <form
                  onSubmit={handleAdicionarSlotCombo}
                  className="bg-white p-4 rounded-xl border border-purple-200 space-y-3"
                >
                  <span className="text-xs font-bold uppercase text-purple-900 block">
                    Adicionar Novo Slot ao Combo
                  </span>
                  <div className="grid grid-cols-3 gap-2">
                    <input
                      type="text"
                      required
                      value={slotNome}
                      onChange={(e) => setSlotNome(e.target.value)}
                      placeholder="Nome do Slot"
                      className="px-3 py-2 rounded-lg border border-slate-300 text-xs"
                    />
                    <input
                      type="number"
                      min={0}
                      value={slotMin}
                      onChange={(e) => setSlotMin(Number(e.target.value))}
                      className="px-3 py-2 rounded-lg border border-slate-300 text-xs"
                    />
                    <input
                      type="number"
                      min={1}
                      value={slotMax}
                      onChange={(e) => setSlotMax(Number(e.target.value))}
                      className="px-3 py-2 rounded-lg border border-slate-300 text-xs"
                    />
                  </div>
                  <div className="space-y-1.5">
                    <span className="text-[11px] font-semibold text-slate-600 block">
                      Selecione os produtos permitidos neste slot:
                    </span>
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-1.5 max-h-36 overflow-y-auto">
                      {produtos
                        .filter((x) => x.id !== produtoSelecionado.id)
                        .map((x) => (
                          <label
                            key={x.id}
                            className="flex items-center gap-2 p-2 rounded-lg border border-slate-200 text-xs cursor-pointer"
                          >
                            <input
                              type="checkbox"
                              checked={slotOpcoesIds.includes(x.id)}
                              onChange={() =>
                                setSlotOpcoesIds((prev) =>
                                  prev.includes(x.id)
                                    ? prev.filter((i) => i !== x.id)
                                    : [...prev, x.id]
                                )
                              }
                            />
                            <span className="truncate">
                              [{x.tipo}] {x.nome}
                            </span>
                          </label>
                        ))}
                    </div>
                  </div>
                  <button
                    type="submit"
                    className="px-4 py-2 rounded-xl bg-purple-700 hover:bg-purple-800 text-white text-xs font-bold"
                  >
                    Salvar Slot no Combo
                  </button>
                </form>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
};
