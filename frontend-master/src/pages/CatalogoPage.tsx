import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Layers,
  Plus,
  Trash2,
  Edit3,
  AlertTriangle,
  CheckCircle2,
  Coffee,
  Cookie,
  Croissant,
  Sparkles,
  RefreshCw,
  Tag,
  X,
} from 'lucide-react';
import { apiRequest } from '../lib/http';
import { parseApiError } from '../lib/errors';
import {
  bigDecimalToInputReais,
  formatBigDecimalReais,
  parseInputToBigDecimalNumber,
} from '../lib/formatters';
import type {
  AdicionalApiDTO,
  CategoriaProdutoApi,
  ProdutoApiDTO,
  SubcategoriaBebidaApi,
  VarianteApiDTO,
} from '../types/api';

const CATEGORIAS_OPCOES: Array<{
  valor: CategoriaProdutoApi;
  label: string;
  descricao: string;
}> = [
  {
    valor: 'BEBIDAS',
    label: 'Bebidas & Cafés',
    descricao: 'Cafés tradicionais, especiais, sucos naturais e refrigerantes',
  },
  {
    valor: 'SALGADOS',
    label: 'Salgados',
    descricao: 'Esfihas, empadas, cuscuz, tapiocas e lanches regionais',
  },
  {
    valor: 'DOCES',
    label: 'Doces & Sobremesas',
    descricao: 'Bolos, brownies, tortas e doces artesanais',
  },
];

const SUBCATEGORIAS_BEBIDAS: Array<{
  valor: SubcategoriaBebidaApi;
  label: string;
}> = [
  { valor: 'TRADICIONAL', label: 'TRADICIONAL — Cafés Tradicionais' },
  { valor: 'ESPECIAL', label: 'ESPECIAL — Cafés Especiais & Cremosos' },
  { valor: 'SUCOS_IOGURTES', label: 'SUCOS_IOGURTES — Sucos Naturais & Iogurtes' },
  { valor: 'REFRIGERANTES', label: 'REFRIGERANTES — Refrescos & Refrigerantes' },
];

const SUGESTOES_TAMANHOS = ['150ml', '300ml', '500ml', 'Pequeno', 'Médio', 'Grande'];

export const CatalogoPage: React.FC = () => {
  const [produtos, setProdutos] = useState<ProdutoApiDTO[]>([]);
  const [adicionaisGlobais, setAdicionaisGlobais] = useState<AdicionalApiDTO[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [saving, setSaving] = useState<boolean>(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  // Filtro de visualização do catálogo
  const [filtroCategoria, setFiltroCategoria] = useState<'TODAS' | CategoriaProdutoApi>('TODAS');
  const [termoBusca, setTermoBusca] = useState<string>('');

  // Estado do Formulário de Produto (Criação ou Edição)
  const [editandoId, setEditandoId] = useState<number | null>(null);
  const [nome, setNome] = useState<string>('');
  const [descricao, setDescricao] = useState<string>('');
  const [precoTexto, setPrecoTexto] = useState<string>('12,00');
  const [categoria, setCategoria] = useState<CategoriaProdutoApi>('BEBIDAS');
  const [subcategoria, setSubcategoria] = useState<SubcategoriaBebidaApi>('TRADICIONAL');

  // Lista temporária de Variantes (Tamanhos) do Produto em edição/criação
  const [variantesForm, setVariantesForm] = useState<VarianteApiDTO[]>([]);
  const [varNomeInput, setVarNomeInput] = useState<string>('');
  const [varPrecoInput, setVarPrecoInput] = useState<string>('');

  // Lista temporária de Adicionais do Produto (suporta PRODUTO ou CATEGORIA_*)
  const [adicionaisForm, setAdicionaisForm] = useState<AdicionalApiDTO[]>([]);
  const [adNomeInput, setAdNomeInput] = useState<string>('');
  const [adPrecoInput, setAdPrecoInput] = useState<string>('');
  const [adEscopoVinculo, setAdEscopoVinculo] = useState<'PRODUTO' | 'CATEGORIA'>('PRODUTO');

  // Formulário rápido de Adicional Global na Coluna Esquerda
  const [globalNome, setGlobalNome] = useState<string>('');
  const [globalPreco, setGlobalPreco] = useState<string>('3,00');
  const [globalCategoria, setGlobalCategoria] = useState<CategoriaProdutoApi>('BEBIDAS');

  const carregarCatalogo = useCallback(async () => {
    setLoading(true);
    setErrorMsg(null);
    try {
      const listaProdutos = await apiRequest<ProdutoApiDTO[]>({
        method: 'GET',
        url: '/produtos',
      });
      const listaNormalizada = Array.isArray(listaProdutos) ? listaProdutos : [];
      setProdutos(listaNormalizada);

      try {
        const globais = await apiRequest<AdicionalApiDTO[]>({
          method: 'GET',
          url: '/produtos/adicionais-globais',
        });
        if (Array.isArray(globais)) {
          setAdicionaisGlobais(globais);
        }
      } catch {
        // Fallback extraindo adicionais globais presentes nos produtos retornados
        const mapaGlobais = new Map<string, AdicionalApiDTO>();
        for (const p of listaNormalizada) {
          for (const ad of p.adicionais || []) {
            if (ad.vinculo && ad.vinculo.toUpperCase().startsWith('CATEGORIA_')) {
              const chave = `${ad.vinculo.toUpperCase()}::${ad.nome.toUpperCase()}`;
              if (!mapaGlobais.has(chave)) {
                mapaGlobais.set(chave, ad);
              }
            }
          }
        }
        setAdicionaisGlobais(Array.from(mapaGlobais.values()));
      }
    } catch (err) {
      setErrorMsg(parseApiError(err).message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    carregarCatalogo();
  }, [carregarCatalogo]);

  const limparFormulario = () => {
    setEditandoId(null);
    setNome('');
    setDescricao('');
    setPrecoTexto('12,00');
    setCategoria('BEBIDAS');
    setSubcategoria('TRADICIONAL');
    setVariantesForm([]);
    setAdicionaisForm([]);
    setVarNomeInput('');
    setVarPrecoInput('');
    setAdNomeInput('');
    setAdPrecoInput('');
    setAdEscopoVinculo('PRODUTO');
  };

  const iniciarEdicaoProduto = (prod: ProdutoApiDTO) => {
    setErrorMsg(null);
    setSuccessMsg(null);
    setEditandoId(prod.id ?? null);
    setNome(prod.nome);
    setDescricao(prod.descricao || '');
    setPrecoTexto(bigDecimalToInputReais(prod.preco));
    setCategoria(prod.categoria);
    if (prod.categoria === 'BEBIDAS') {
      const subValida = SUBCATEGORIAS_BEBIDAS.some((s) => s.valor === prod.subcategoria)
        ? (prod.subcategoria as SubcategoriaBebidaApi)
        : 'TRADICIONAL';
      setSubcategoria(subValida);
    }
    setVariantesForm(
      (prod.variantes || []).map((v) => ({
        id: v.id,
        nome: v.nome,
        preco: v.preco,
      }))
    );
    // Na edição do produto, carregamos apenas os adicionais específicos dele (PRODUTO)
    // para evitar duplicar os globais que já vêm mesclados no GET /produtos
    setAdicionaisForm(
      (prod.adicionais || [])
        .filter((a) => !a.vinculo || a.vinculo.toUpperCase() === 'PRODUTO')
        .map((a) => ({
          id: a.id,
          nome: a.nome,
          preco: a.preco,
          vinculo: 'PRODUTO',
        }))
    );
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const handleAdicionarVarianteNoForm = () => {
    const nomeLimpo = varNomeInput.trim();
    if (!nomeLimpo) {
      setErrorMsg('Informe o nome/tamanho da variante (ex.: 300ml, Médio).');
      return;
    }
    const precoNum = parseInputToBigDecimalNumber(varPrecoInput || precoTexto);
    if (precoNum < 0) {
      setErrorMsg('O preço da variante não pode ser negativo.');
      return;
    }
    setErrorMsg(null);
    setVariantesForm((prev) => [
      ...prev,
      { nome: nomeLimpo, preco: precoNum },
    ]);
    setVarNomeInput('');
    setVarPrecoInput('');
  };

  const handleRemoverVarianteDoForm = (index: number) => {
    setVariantesForm((prev) => prev.filter((_, idx) => idx !== index));
  };

  const handleAdicionarOpcionalNoForm = () => {
    const nomeLimpo = adNomeInput.trim();
    if (!nomeLimpo) {
      setErrorMsg('Informe o nome do adicional/opcional (ex.: Mel, Chantilly, Queijo Coalho).');
      return;
    }
    const precoNum = parseInputToBigDecimalNumber(adPrecoInput || '0');
    if (precoNum < 0) {
      setErrorMsg('O preço do adicional não pode ser negativo.');
      return;
    }
    const vinculoCalculado =
      adEscopoVinculo === 'PRODUTO' ? 'PRODUTO' : `CATEGORIA_${categoria}`;

    setErrorMsg(null);
    setAdicionaisForm((prev) => [
      ...prev,
      {
        nome: nomeLimpo,
        preco: precoNum,
        vinculo: vinculoCalculado,
      },
    ]);
    setAdNomeInput('');
    setAdPrecoInput('');
  };

  const handleRemoverOpcionalDoForm = (index: number) => {
    setAdicionaisForm((prev) => prev.filter((_, idx) => idx !== index));
  };

  const handleSalvarProduto = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg(null);
    setSuccessMsg(null);

    const nomeLimpo = nome.trim();
    if (!nomeLimpo) {
      setErrorMsg('O nome do produto é obrigatório.');
      return;
    }

    const precoBase = parseInputToBigDecimalNumber(precoTexto);
    if (precoBase < 0) {
      setErrorMsg('O preço base do produto não pode ser negativo.');
      return;
    }

    const payload: ProdutoApiDTO = {
      nome: nomeLimpo,
      descricao: descricao.trim() ? descricao.trim() : null,
      preco: precoBase,
      categoria,
      subcategoria: categoria === 'BEBIDAS' ? subcategoria : null,
      variantes: variantesForm.map((v) => ({
        nome: v.nome.trim(),
        preco: parseInputToBigDecimalNumber(String(v.preco)),
      })),
      adicionais: adicionaisForm.map((a) => ({
        nome: a.nome.trim(),
        preco: parseInputToBigDecimalNumber(String(a.preco)),
        vinculo:
          a.vinculo && a.vinculo.toUpperCase().startsWith('CATEGORIA_')
            ? `CATEGORIA_${categoria}`
            : 'PRODUTO',
      })),
    };

    setSaving(true);
    try {
      if (editandoId !== null) {
        const atualizado = await apiRequest<ProdutoApiDTO>({
          method: 'PUT',
          url: `/produtos/${editandoId}`,
          data: payload,
        });
        setSuccessMsg(
          `Produto "${atualizado.nome}" (#${atualizado.id}) atualizado com sucesso no MySQL!`
        );
      } else {
        const criado = await apiRequest<ProdutoApiDTO>({
          method: 'POST',
          url: '/produtos',
          data: payload,
        });
        setSuccessMsg(
          `Produto "${criado.nome}" (#${criado.id}) cadastrado com sucesso por ${formatBigDecimalReais(
            criado.preco
          )}!`
        );
      }
      limparFormulario();
      await carregarCatalogo();
    } catch (err) {
      setErrorMsg(parseApiError(err).message);
    } finally {
      setSaving(false);
    }
  };

  const handleDeletarProduto = async (prod: ProdutoApiDTO) => {
    if (!prod.id) return;
    setErrorMsg(null);
    setSuccessMsg(null);
    try {
      await apiRequest<void>({
        method: 'DELETE',
        url: `/produtos/${prod.id}`,
      });
      if (editandoId === prod.id) {
        limparFormulario();
      }
      setSuccessMsg(`Produto "${prod.nome}" removido do catálogo.`);
      await carregarCatalogo();
    } catch (err) {
      setErrorMsg(parseApiError(err).message);
    }
  };

  const handleCriarAdicionalGlobal = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg(null);
    setSuccessMsg(null);
    const nomeLimpo = globalNome.trim();
    if (!nomeLimpo) {
      setErrorMsg('Informe o nome do adicional global da categoria.');
      return;
    }
    const precoNum = parseInputToBigDecimalNumber(globalPreco);
    try {
      const criado = await apiRequest<AdicionalApiDTO>({
        method: 'POST',
        url: '/produtos/adicionais-globais',
        data: {
          nome: nomeLimpo,
          preco: precoNum,
          vinculo: `CATEGORIA_${globalCategoria}`,
        },
      });
      setGlobalNome('');
      setGlobalPreco('3,00');
      setSuccessMsg(
        `Adicional global "${criado.nome}" (${formatBigDecimalReais(
          criado.preco
        )}) vinculado a ${criado.vinculo}!`
      );
      await carregarCatalogo();
    } catch (err) {
      setErrorMsg(parseApiError(err).message);
    }
  };

  const handleRemoverAdicionalGlobal = async (ad: AdicionalApiDTO) => {
    if (!ad.id) return;
    setErrorMsg(null);
    setSuccessMsg(null);
    try {
      await apiRequest<void>({
        method: 'DELETE',
        url: `/produtos/adicionais-globais/${ad.id}`,
      });
      setSuccessMsg(`Adicional global "${ad.nome}" removido.`);
      await carregarCatalogo();
    } catch (err) {
      setErrorMsg(parseApiError(err).message);
    }
  };

  const produtosFiltrados = useMemo(() => {
    return produtos.filter((p) => {
      const matchCat =
        filtroCategoria === 'TODAS' || p.categoria === filtroCategoria;
      const matchBusca =
        !termoBusca.trim() ||
        p.nome.toLowerCase().includes(termoBusca.toLowerCase()) ||
        (p.descricao || '').toLowerCase().includes(termoBusca.toLowerCase()) ||
        (p.subcategoria || '').toLowerCase().includes(termoBusca.toLowerCase());
      return matchCat && matchBusca;
    });
  }, [produtos, filtroCategoria, termoBusca]);

  return (
    <div className="space-y-6">
      {/* Cabeçalho */}
      <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2 text-amber-700 text-xs font-bold uppercase tracking-wider">
            <Layers className="w-4 h-4" />
            <span>Governança Centralizada de Cardápio (Web Master ➔ MySQL ➔ App Android)</span>
          </div>
          <h1 className="text-2xl font-extrabold text-slate-900 mt-1">
            Gestão de Produtos, Variantes de Tamanho &amp; Adicionais (`BigDecimal`)
          </h1>
          <p className="text-xs text-slate-500 mt-1">
            Todos os produtos, tamanhos e adicionais cadastrados aqui alimentam em tempo real a grade de 2 colunas do aplicativo mobile.
          </p>
        </div>

        <button
          type="button"
          onClick={carregarCatalogo}
          className="px-4 py-2.5 rounded-xl border border-slate-200 hover:bg-slate-50 text-xs font-bold text-slate-700 flex items-center gap-2 self-start sm:self-auto"
        >
          <RefreshCw className="w-4 h-4" />
          <span>Recarregar Banco</span>
        </button>
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
        {/* Coluna Esquerda (4 cols): Resumo de Categorias & Gestão de Adicionais Globais (CATEGORIA_*) */}
        <div className="lg:col-span-4 space-y-5">
          {/* Card 1: Estrutura Canônica de Categorias */}
          <div className="bg-white rounded-2xl border border-slate-200 p-5 shadow-sm space-y-4">
            <h2 className="text-base font-bold text-slate-900 flex items-center justify-between">
              <span>Categorias Canônicas</span>
              <span className="text-[11px] font-mono text-amber-700 font-bold">
                Enum CategoriaProduto
              </span>
            </h2>

            <div className="space-y-3">
              {CATEGORIAS_OPCOES.map((cat) => {
                const qtd = produtos.filter((p) => p.categoria === cat.valor).length;
                return (
                  <div
                    key={cat.valor}
                    className="rounded-xl border border-slate-200 p-3.5 bg-slate-50/70 space-y-2"
                  >
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        {cat.valor === 'BEBIDAS' && (
                          <Coffee className="w-4 h-4 text-amber-700" />
                        )}
                        {cat.valor === 'SALGADOS' && (
                          <Croissant className="w-4 h-4 text-orange-600" />
                        )}
                        {cat.valor === 'DOCES' && (
                          <Cookie className="w-4 h-4 text-pink-600" />
                        )}
                        <span className="text-xs font-extrabold text-slate-900">
                          {cat.label}
                        </span>
                        <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-slate-200 text-slate-700">
                          {cat.valor}
                        </span>
                      </div>
                      <span className="text-xs font-bold text-slate-600">
                        {qtd} item(ns)
                      </span>
                    </div>
                    <p className="text-[11px] text-slate-500">{cat.descricao}</p>

                    {cat.valor === 'BEBIDAS' && (
                      <div className="pt-1.5 pl-3 border-l-2 border-amber-400 space-y-1">
                        <span className="block text-[10px] font-bold uppercase text-amber-800">
                          Subcategorias de Bebidas (Filtro Mobile):
                        </span>
                        {SUBCATEGORIAS_BEBIDAS.map((sub) => (
                          <div
                            key={sub.valor}
                            className="text-[11px] text-slate-700 flex items-center justify-between bg-white px-2.5 py-1 rounded border border-slate-200"
                          >
                            <span className="font-mono font-semibold text-slate-800">
                              {sub.valor}
                            </span>
                            <span className="text-slate-500 text-[10px]">
                              {
                                produtos.filter(
                                  (p) =>
                                    p.categoria === 'BEBIDAS' &&
                                    p.subcategoria?.toUpperCase() === sub.valor
                                ).length
                              }{' '}
                              prod.
                            </span>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          </div>

          {/* Card 2: Governança de Adicionais Globais por Categoria (CATEGORIA_*) */}
          <div className="bg-white rounded-2xl border border-slate-200 p-5 shadow-sm space-y-4">
            <div>
              <h2 className="text-base font-bold text-slate-900 flex items-center gap-2">
                <Sparkles className="w-4 h-4 text-amber-600" />
                <span>Adicionais Globais de Categoria</span>
              </h2>
              <p className="text-xs text-slate-500 mt-0.5">
                Adicionais com vínculo <code className="font-mono">CATEGORIA_*</code> aparecem automaticamente em todos os produtos daquela categoria no App Android.
              </p>
            </div>

            <form
              onSubmit={handleCriarAdicionalGlobal}
              className="space-y-2.5 bg-slate-50 p-3.5 rounded-xl border border-slate-200"
            >
              <span className="text-[11px] font-bold uppercase text-slate-600 block">
                Novo Adicional Global
              </span>
              <div className="grid grid-cols-3 gap-2">
                <input
                  type="text"
                  required
                  value={globalNome}
                  onChange={(e) => setGlobalNome(e.target.value)}
                  placeholder="Ex.: Leite Condensado"
                  className="col-span-2 px-3 py-2 rounded-xl border border-slate-300 text-xs bg-white"
                />
                <input
                  type="text"
                  required
                  value={globalPreco}
                  onChange={(e) => setGlobalPreco(e.target.value)}
                  placeholder="3,00"
                  className="px-3 py-2 rounded-xl border border-slate-300 text-xs font-bold bg-white"
                />
              </div>
              <div className="flex gap-2">
                <select
                  value={globalCategoria}
                  onChange={(e) =>
                    setGlobalCategoria(e.target.value as CategoriaProdutoApi)
                  }
                  className="flex-1 px-3 py-2 rounded-xl border border-slate-300 text-xs bg-white font-semibold"
                >
                  <option value="BEBIDAS">CATEGORIA_BEBIDAS</option>
                  <option value="SALGADOS">CATEGORIA_SALGADOS</option>
                  <option value="DOCES">CATEGORIA_DOCES</option>
                </select>
                <button
                  type="submit"
                  className="px-3.5 py-2 rounded-xl bg-slate-900 hover:bg-slate-800 text-white text-xs font-bold flex items-center gap-1 shrink-0"
                >
                  <Plus className="w-3.5 h-3.5" />
                  <span>Salvar Global</span>
                </button>
              </div>
            </form>

            <div className="space-y-2 max-h-64 overflow-y-auto pr-1">
              {adicionaisGlobais.length === 0 ? (
                <p className="text-xs text-slate-400 text-center py-3">
                  Nenhum adicional global cadastrado ainda.
                </p>
              ) : (
                adicionaisGlobais.map((ad, idx) => (
                  <div
                    key={ad.id ?? `${ad.vinculo}-${ad.nome}-${idx}`}
                    className="flex items-center justify-between px-3 py-2 rounded-xl border border-slate-200 bg-white text-xs"
                  >
                    <div>
                      <span className="font-bold text-slate-800">{ad.nome}</span>
                      <span className="ml-2 text-amber-700 font-bold">
                        +{formatBigDecimalReais(ad.preco)}
                      </span>
                      <span className="block text-[10px] font-mono text-slate-500">
                        {ad.vinculo}
                      </span>
                    </div>
                    {ad.id && (
                      <button
                        type="button"
                        onClick={() => handleRemoverAdicionalGlobal(ad)}
                        className="p-1.5 text-slate-400 hover:text-rose-600 transition"
                        title="Remover adicional global"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    )}
                  </div>
                ))
              )}
            </div>
          </div>
        </div>

        {/* Coluna Direita (8 cols): Formulário Completo de Produto + Grade de Produtos Cadastrados */}
        <div className="lg:col-span-8 space-y-6">
          <form
            onSubmit={handleSalvarProduto}
            className="bg-white rounded-2xl border border-slate-200 p-6 shadow-sm space-y-5"
          >
            <div className="flex items-center justify-between border-b border-slate-200 pb-3">
              <div className="flex items-center gap-2">
                {editandoId !== null ? (
                  <Edit3 className="w-5 h-5 text-amber-600" />
                ) : (
                  <Plus className="w-5 h-5 text-amber-600" />
                )}
                <h2 className="text-base font-extrabold text-slate-900">
                  {editandoId !== null
                    ? `Editando Produto #${editandoId}`
                    : 'Cadastrar Novo Produto no Cardápio'}
                </h2>
              </div>
              {editandoId !== null && (
                <button
                  type="button"
                  onClick={limparFormulario}
                  className="px-3 py-1 rounded-lg bg-slate-100 hover:bg-slate-200 text-xs font-bold text-slate-700 flex items-center gap-1"
                >
                  <X className="w-3.5 h-3.5" />
                  <span>Cancelar Edição</span>
                </button>
              )}
            </div>

            {/* Dados Básicos: Nome, Preço Base, Categoria e Subcategoria Condicional */}
            <div className="grid grid-cols-1 sm:grid-cols-12 gap-3.5">
              <div className="sm:col-span-5">
                <label className="block text-[11px] font-bold uppercase text-slate-600 mb-1">
                  Nome do Produto *
                </label>
                <input
                  type="text"
                  required
                  value={nome}
                  onChange={(e) => setNome(e.target.value)}
                  placeholder="Ex.: Cappuccino Cremoso Artesanal"
                  className="w-full px-3.5 py-2.5 rounded-xl border border-slate-300 text-xs font-semibold focus:border-amber-600 outline-none"
                />
              </div>

              <div className="sm:col-span-3">
                <label className="block text-[11px] font-bold uppercase text-slate-600 mb-1">
                  Preço Base (R$) *
                </label>
                <input
                  type="text"
                  required
                  value={precoTexto}
                  onChange={(e) => setPrecoTexto(e.target.value)}
                  placeholder="12,00"
                  className="w-full px-3.5 py-2.5 rounded-xl border border-slate-300 text-xs font-extrabold text-amber-800 focus:border-amber-600 outline-none"
                />
              </div>

              <div className="sm:col-span-4">
                <label className="block text-[11px] font-bold uppercase text-slate-600 mb-1">
                  Categoria Principal *
                </label>
                <select
                  value={categoria}
                  onChange={(e) =>
                    setCategoria(e.target.value as CategoriaProdutoApi)
                  }
                  className="w-full px-3.5 py-2.5 rounded-xl border border-slate-300 text-xs font-bold bg-white focus:border-amber-600 outline-none"
                >
                  <option value="BEBIDAS">BEBIDAS (Bebidas &amp; Cafés)</option>
                  <option value="SALGADOS">SALGADOS (Lanches &amp; Salgados)</option>
                  <option value="DOCES">DOCES (Doces &amp; Bolos)</option>
                </select>
              </div>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-12 gap-3.5">
              <div
                className={
                  categoria === 'BEBIDAS' ? 'sm:col-span-7' : 'sm:col-span-12'
                }
              >
                <label className="block text-[11px] font-bold uppercase text-slate-600 mb-1">
                  Descrição / Notas Sensoriais
                </label>
                <input
                  type="text"
                  value={descricao}
                  onChange={(e) => setDescricao(e.target.value)}
                  placeholder="Ex.: Café 100% arábica com notas de chocolate e avelã"
                  className="w-full px-3.5 py-2.5 rounded-xl border border-slate-300 text-xs focus:border-amber-600 outline-none"
                />
              </div>

              {categoria === 'BEBIDAS' && (
                <div className="sm:col-span-5">
                  <label className="block text-[11px] font-bold uppercase text-amber-800 mb-1">
                    Subcategoria de Bebida *
                  </label>
                  <select
                    value={subcategoria}
                    onChange={(e) =>
                      setSubcategoria(e.target.value as SubcategoriaBebidaApi)
                    }
                    className="w-full px-3.5 py-2.5 rounded-xl border border-amber-400 bg-amber-50/50 text-xs font-bold text-slate-900 focus:border-amber-600 outline-none"
                  >
                    {SUBCATEGORIAS_BEBIDAS.map((sub) => (
                      <option key={sub.valor} value={sub.valor}>
                        {sub.label}
                      </option>
                    ))}
                  </select>
                </div>
              )}
            </div>

            {/* Construtor Dinâmico de Variantes (Tamanhos) e Adicionais */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4 pt-2 border-t border-slate-200">
              {/* Bloco 1: Variantes / Tamanhos */}
              <div className="rounded-2xl bg-slate-50 border border-slate-200 p-4 space-y-3">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-extrabold uppercase text-slate-800 flex items-center gap-1.5">
                    <Tag className="w-3.5 h-3.5 text-amber-600" />
                    <span>Tamanhos / Variantes</span>
                  </span>
                  <span className="text-[11px] text-slate-500">
                    {variantesForm.length} adicionado(s)
                  </span>
                </div>

                {/* Atalhos Rápidos de Tamanho */}
                <div className="flex flex-wrap gap-1">
                  {SUGESTOES_TAMANHOS.map((sug) => (
                    <button
                      key={sug}
                      type="button"
                      onClick={() => setVarNomeInput(sug)}
                      className="px-2 py-0.5 rounded-md bg-white border border-slate-200 hover:border-amber-500 text-[10px] font-semibold text-slate-600"
                    >
                      +{sug}
                    </button>
                  ))}
                </div>

                <div className="flex gap-2">
                  <input
                    type="text"
                    value={varNomeInput}
                    onChange={(e) => setVarNomeInput(e.target.value)}
                    placeholder="Ex.: 300ml"
                    className="flex-1 px-3 py-2 rounded-xl border border-slate-300 text-xs bg-white"
                  />
                  <input
                    type="text"
                    value={varPrecoInput}
                    onChange={(e) => setVarPrecoInput(e.target.value)}
                    placeholder="Preço (R$)"
                    className="w-24 px-3 py-2 rounded-xl border border-slate-300 text-xs font-bold bg-white"
                  />
                  <button
                    type="button"
                    onClick={handleAdicionarVarianteNoForm}
                    className="px-3 py-2 rounded-xl bg-amber-600 hover:bg-amber-700 text-white text-xs font-bold"
                  >
                    +
                  </button>
                </div>

                <div className="space-y-1.5 max-h-40 overflow-y-auto">
                  {variantesForm.length === 0 ? (
                    <p className="text-[11px] text-slate-400 italic">
                      Nenhum tamanho específico (usará o Preço Base).
                    </p>
                  ) : (
                    variantesForm.map((v, idx) => (
                      <div
                        key={`${v.nome}-${idx}`}
                        className="flex items-center justify-between px-3 py-1.5 rounded-lg bg-white border border-slate-200 text-xs"
                      >
                        <span className="font-semibold text-slate-800">
                          {v.nome}
                        </span>
                        <div className="flex items-center gap-2">
                          <span className="font-bold text-amber-700">
                            {formatBigDecimalReais(v.preco)}
                          </span>
                          <button
                            type="button"
                            onClick={() => handleRemoverVarianteDoForm(idx)}
                            className="text-slate-400 hover:text-rose-600"
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                          </button>
                        </div>
                      </div>
                    ))
                  )}
                </div>
              </div>

              {/* Bloco 2: Opcionais / Adicionais */}
              <div className="rounded-2xl bg-slate-50 border border-slate-200 p-4 space-y-3">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-extrabold uppercase text-slate-800 flex items-center gap-1.5">
                    <Sparkles className="w-3.5 h-3.5 text-amber-600" />
                    <span>Opcionais / Adicionais</span>
                  </span>
                  <span className="text-[11px] text-slate-500">
                    {adicionaisForm.length} no produto
                  </span>
                </div>

                {/* Escopo do Adicional: PRODUTO vs CATEGORIA_* */}
                <div className="grid grid-cols-2 gap-1.5 bg-white p-1 rounded-xl border border-slate-200">
                  <button
                    type="button"
                    onClick={() => setAdEscopoVinculo('PRODUTO')}
                    className={`py-1.5 px-2 rounded-lg text-[11px] font-bold transition ${
                      adEscopoVinculo === 'PRODUTO'
                        ? 'bg-slate-900 text-white'
                        : 'text-slate-600 hover:bg-slate-100'
                    }`}
                  >
                    Apenas deste Produto
                  </button>
                  <button
                    type="button"
                    onClick={() => setAdEscopoVinculo('CATEGORIA')}
                    className={`py-1.5 px-2 rounded-lg text-[11px] font-bold transition ${
                      adEscopoVinculo === 'CATEGORIA'
                        ? 'bg-amber-600 text-white'
                        : 'text-slate-600 hover:bg-slate-100'
                    }`}
                  >
                    Geral de {categoria}
                  </button>
                </div>

                <div className="flex gap-2">
                  <input
                    type="text"
                    value={adNomeInput}
                    onChange={(e) => setAdNomeInput(e.target.value)}
                    placeholder="Ex.: Chantilly / Mel"
                    className="flex-1 px-3 py-2 rounded-xl border border-slate-300 text-xs bg-white"
                  />
                  <input
                    type="text"
                    value={adPrecoInput}
                    onChange={(e) => setAdPrecoInput(e.target.value)}
                    placeholder="+ R$"
                    className="w-24 px-3 py-2 rounded-xl border border-slate-300 text-xs font-bold bg-white"
                  />
                  <button
                    type="button"
                    onClick={handleAdicionarOpcionalNoForm}
                    className="px-3 py-2 rounded-xl bg-amber-600 hover:bg-amber-700 text-white text-xs font-bold"
                  >
                    +
                  </button>
                </div>

                <div className="space-y-1.5 max-h-40 overflow-y-auto">
                  {adicionaisForm.length === 0 ? (
                    <p className="text-[11px] text-slate-400 italic">
                      Nenhum adicional exclusivo vinculado neste formulário.
                    </p>
                  ) : (
                    adicionaisForm.map((a, idx) => (
                      <div
                        key={`${a.nome}-${idx}`}
                        className="flex items-center justify-between px-3 py-1.5 rounded-lg bg-white border border-slate-200 text-xs"
                      >
                        <div>
                          <span className="font-semibold text-slate-800">
                            {a.nome}
                          </span>
                          <span className="ml-1.5 text-[10px] font-mono px-1.5 py-0.5 rounded bg-slate-100 text-slate-600">
                            {a.vinculo}
                          </span>
                        </div>
                        <div className="flex items-center gap-2">
                          <span className="font-bold text-amber-700">
                            +{formatBigDecimalReais(a.preco)}
                          </span>
                          <button
                            type="button"
                            onClick={() => handleRemoverOpcionalDoForm(idx)}
                            className="text-slate-400 hover:text-rose-600"
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                          </button>
                        </div>
                      </div>
                    ))
                  )}
                </div>
              </div>
            </div>

            <div className="flex items-center justify-end gap-3 pt-2 border-t border-slate-100">
              {editandoId !== null && (
                <button
                  type="button"
                  onClick={limparFormulario}
                  className="px-4 py-2.5 rounded-xl border border-slate-300 text-xs font-bold text-slate-700 hover:bg-slate-50"
                >
                  Descartar Alterações
                </button>
              )}
              <button
                type="submit"
                disabled={saving}
                className="px-6 py-2.5 rounded-xl bg-amber-600 hover:bg-amber-700 disabled:opacity-50 text-white text-xs font-extrabold transition shadow-sm"
              >
                {saving
                  ? 'Salvando no MySQL...'
                  : editandoId !== null
                  ? 'Salvar Alterações do Produto'
                  : 'Cadastrar Produto no Banco'}
              </button>
            </div>
          </form>

          {/* Barra de Filtros e Pesquisa de Produtos Cadastrados */}
          <div className="bg-white rounded-2xl border border-slate-200 p-4 shadow-sm flex flex-col sm:flex-row sm:items-center justify-between gap-3">
            <div className="flex items-center gap-1.5 overflow-x-auto">
              {(
                ['TODAS', 'BEBIDAS', 'SALGADOS', 'DOCES'] as Array<
                  'TODAS' | CategoriaProdutoApi
                >
              ).map((cat) => (
                <button
                  key={cat}
                  type="button"
                  onClick={() => setFiltroCategoria(cat)}
                  className={`px-3.5 py-1.5 rounded-xl text-xs font-bold transition ${
                    filtroCategoria === cat
                      ? 'bg-slate-900 text-white'
                      : 'bg-slate-100 text-slate-700 hover:bg-slate-200'
                  }`}
                >
                  {cat === 'TODAS' ? `Todos (${produtos.length})` : cat}
                </button>
              ))}
            </div>

            <input
              type="text"
              value={termoBusca}
              onChange={(e) => setTermoBusca(e.target.value)}
              placeholder="Filtrar por nome ou subcategoria..."
              className="px-3.5 py-1.5 rounded-xl border border-slate-300 text-xs w-full sm:w-64"
            />
          </div>

          {/* Grade de Produtos Cadastrados no Banco */}
          {loading ? (
            <div className="bg-white rounded-2xl border border-slate-200 p-8 text-center text-xs text-slate-500">
              Carregando catálogo do banco de dados MySQL...
            </div>
          ) : produtosFiltrados.length === 0 ? (
            <div className="bg-white rounded-2xl border border-slate-200 p-10 text-center text-sm text-slate-500">
              Nenhum produto encontrado para o filtro selecionado. Utilize o formulário acima para cadastrar novos itens no cardápio!
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {produtosFiltrados.map((p) => (
                <div
                  key={p.id ?? p.nome}
                  className="bg-white rounded-2xl border border-slate-200 p-5 flex flex-col justify-between gap-4 shadow-sm hover:border-amber-400 transition"
                >
                  <div className="space-y-2.5">
                    <div className="flex items-center justify-between gap-2">
                      <div className="flex items-center gap-1.5 flex-wrap">
                        <span className="px-2 py-0.5 rounded bg-slate-100 text-slate-700 font-mono text-[10px] font-bold">
                          #{p.id}
                        </span>
                        <span className="px-2 py-0.5 rounded bg-amber-100 text-amber-900 text-[10px] font-bold">
                          {p.categoria}
                        </span>
                        {p.subcategoria && (
                          <span className="px-2 py-0.5 rounded bg-sky-100 text-sky-900 text-[10px] font-mono font-bold">
                            {p.subcategoria}
                          </span>
                        )}
                      </div>
                      <span className="text-lg font-extrabold text-amber-700">
                        {formatBigDecimalReais(p.preco)}
                      </span>
                    </div>

                    <h3 className="text-base font-extrabold text-slate-900">
                      {p.nome}
                    </h3>

                    {p.descricao && (
                      <p className="text-xs text-slate-500">{p.descricao}</p>
                    )}

                    {/* Chips de Variantes */}
                    {p.variantes && p.variantes.length > 0 && (
                      <div className="pt-1 space-y-1">
                        <span className="block text-[10px] font-bold uppercase text-slate-400">
                          Tamanhos Disponíveis:
                        </span>
                        <div className="flex flex-wrap gap-1.5">
                          {p.variantes.map((v, i) => (
                            <span
                              key={v.id ?? `${v.nome}-${i}`}
                              className="px-2 py-0.5 rounded-lg bg-slate-100 border border-slate-200 text-[11px] font-semibold text-slate-800"
                            >
                              {v.nome}:{' '}
                              <strong className="text-amber-800">
                                {formatBigDecimalReais(v.preco)}
                              </strong>
                            </span>
                          ))}
                        </div>
                      </div>
                    )}

                    {/* Chips de Adicionais */}
                    {p.adicionais && p.adicionais.length > 0 && (
                      <div className="pt-1 space-y-1">
                        <span className="block text-[10px] font-bold uppercase text-slate-400">
                          Adicionais Ativos ({p.adicionais.length}):
                        </span>
                        <div className="flex flex-wrap gap-1.5">
                          {p.adicionais.map((a, i) => (
                            <span
                              key={a.id ?? `${a.nome}-${i}`}
                              className={`px-2 py-0.5 rounded-lg text-[11px] font-medium border ${
                                a.vinculo?.startsWith('CATEGORIA_')
                                  ? 'bg-amber-50 border-amber-200 text-amber-900'
                                  : 'bg-slate-50 border-slate-200 text-slate-700'
                              }`}
                            >
                              +{a.nome} ({formatBigDecimalReais(a.preco)})
                            </span>
                          ))}
                        </div>
                      </div>
                    )}
                  </div>

                  <div className="pt-3 border-t border-slate-100 flex items-center justify-between gap-2">
                    <button
                      type="button"
                      onClick={() => iniciarEdicaoProduto(p)}
                      className="px-3.5 py-1.5 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-800 text-xs font-bold flex items-center gap-1.5 transition"
                    >
                      <Edit3 className="w-3.5 h-3.5" />
                      <span>Editar Produto</span>
                    </button>

                    <button
                      type="button"
                      onClick={() => handleDeletarProduto(p)}
                      className="px-3 py-1.5 rounded-xl border border-rose-200 hover:bg-rose-50 text-rose-700 text-xs font-bold flex items-center gap-1 transition"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                      <span>Excluir</span>
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
