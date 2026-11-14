import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  ShoppingBag,
  Plus,
  Trash2,
  CheckCircle2,
  AlertTriangle,
  Search,
  Utensils,
  Coffee,
  Sparkles,
  User,
} from 'lucide-react';
import { apiRequest } from '../lib/http';
import { parseApiError } from '../lib/errors';
import {
  formatBigDecimalReais,
  roundBigDecimalHalfUp,
} from '../lib/formatters';
import type {
  AdicionalApiDTO,
  CategoriaProdutoApi,
  ItemPedidoApiDTO,
  PedidoRequestApiDTO,
  PedidoResponseApiDTO,
  ProdutoApiDTO,
  VarianteApiDTO,
} from '../types/api';

interface CarrinhoItem {
  localId: string;
  produto: ProdutoApiDTO;
  quantidade: number;
  varianteSelecionada: VarianteApiDTO | null;
  tipoGraoEscolhido: string | null;
  adicionaisSelecionados: AdicionalApiDTO[];
  pagadorItem: string;
  observacoes: string;
  precoUnitarioFinal: number;
}

const OPCOES_GRAO = [
  'Tradicional (Blend da Casa)',
  'Arábica Especial 100%',
  'Descafeinado',
];

export const PdvPage: React.FC = () => {
  const navigate = useNavigate();

  const [produtos, setProdutos] = useState<ProdutoApiDTO[]>([]);
  const [adicionaisGlobais, setAdicionaisGlobais] = useState<AdicionalApiDTO[]>([]);
  const [categoriaFiltro, setCategoriaFiltro] = useState<'TODAS' | CategoriaProdutoApi>('TODAS');
  const [busca, setBusca] = useState('');

  // Comanda / Carrinho
  const [carrinho, setCarrinho] = useState<CarrinhoItem[]>([]);
  const [numeroMesa, setNumeroMesa] = useState<string>('1');

  // Modal de Personalização do Produto
  const [produtoCustomizando, setProdutoCustomizando] = useState<ProdutoApiDTO | null>(null);
  const [varianteIdxModal, setVarianteIdxModal] = useState<number>(-1);
  const [graoModal, setGraoModal] = useState<string>(OPCOES_GRAO[0]);
  const [adicionaisModalIds, setAdicionaisModalIds] = useState<Record<string, boolean>>({});
  const [qtdModal, setQtdModal] = useState<number>(1);
  const [pagadorModal, setPagadorModal] = useState<string>('Mesa Geral');
  const [obsItemModal, setObsItemModal] = useState<string>('');

  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [pedidoRecemCriado, setPedidoRecemCriado] = useState<PedidoResponseApiDTO | null>(null);

  const carregarCatalogo = useCallback(async () => {
    try {
      const [prods, globais] = await Promise.all([
        apiRequest<ProdutoApiDTO[]>({ method: 'GET', url: '/produtos' }),
        apiRequest<AdicionalApiDTO[]>({
          method: 'GET',
          url: '/produtos/adicionais-globais',
        }).catch(() => [] as AdicionalApiDTO[]),
      ]);
      setProdutos(Array.isArray(prods) ? prods : []);
      setAdicionaisGlobais(Array.isArray(globais) ? globais : []);
    } catch (err) {
      setErrorMsg(parseApiError(err).message);
    }
  }, []);

  useEffect(() => {
    carregarCatalogo();
  }, [carregarCatalogo]);

  const abrirModalProduto = (prod: ProdutoApiDTO) => {
    if (prod.ativo === false) return;
    setErrorMsg(null);
    setProdutoCustomizando(prod);
    setVarianteIdxModal(prod.variantes && prod.variantes.length > 0 ? 0 : -1);
    setGraoModal(OPCOES_GRAO[0]);
    setAdicionaisModalIds({});
    setQtdModal(1);
    setPagadorModal('Mesa Geral');
    setObsItemModal('');
  };

  const adicionaisDisponiveisParaModal = useMemo(() => {
    if (!produtoCustomizando) return [];
    const doProduto = produtoCustomizando.adicionais || [];
    const escopoEsperado = `CATEGORIA_${produtoCustomizando.categoria}`;
    const daCategoria = adicionaisGlobais.filter((g) => g.escopo === escopoEsperado);

    const mapa = new Map<string, AdicionalApiDTO>();
    for (const a of [...doProduto, ...daCategoria]) {
      const key = `${a.nome.toLowerCase()}-${a.escopo || 'PRODUTO'}`;
      if (!mapa.has(key)) {
        mapa.set(key, a);
      }
    }
    return Array.from(mapa.values());
  }, [produtoCustomizando, adicionaisGlobais]);

  const precoUnitarioPreviewModal = useMemo(() => {
    if (!produtoCustomizando) return 0;
    const vars = produtoCustomizando.variantes || [];
    const precoBaseOuVariante =
      varianteIdxModal >= 0 && vars[varianteIdxModal]
        ? Number(vars[varianteIdxModal].preco)
        : Number(produtoCustomizando.precoBase || 0);

    let somaAdicionais = 0;
    adicionaisDisponiveisParaModal.forEach((adic, idx) => {
      const key = `${adic.id ?? idx}-${adic.nome}`;
      if (adicionaisModalIds[key]) {
        somaAdicionais = roundBigDecimalHalfUp(somaAdicionais + Number(adic.preco || 0));
      }
    });

    return roundBigDecimalHalfUp(precoBaseOuVariante + somaAdicionais);
  }, [
    produtoCustomizando,
    varianteIdxModal,
    adicionaisDisponiveisParaModal,
    adicionaisModalIds,
  ]);

  const confirmarItemNoCarrinho = () => {
    if (!produtoCustomizando) return;
    const vars = produtoCustomizando.variantes || [];
    const varianteSelecionada =
      varianteIdxModal >= 0 && vars[varianteIdxModal]
        ? vars[varianteIdxModal]
        : null;

    const selecionados: AdicionalApiDTO[] = [];
    adicionaisDisponiveisParaModal.forEach((adic, idx) => {
      const key = `${adic.id ?? idx}-${adic.nome}`;
      if (adicionaisModalIds[key]) {
        selecionados.push(adic);
      }
    });

    const novoItem: CarrinhoItem = {
      localId: `${produtoCustomizando.id}-${Date.now()}`,
      produto: produtoCustomizando,
      quantidade: qtdModal,
      varianteSelecionada,
      tipoGraoEscolhido:
        produtoCustomizando.categoria === 'BEBIDAS' ? graoModal : null,
      adicionaisSelecionados: selecionados,
      pagadorItem: pagadorModal.trim() || 'Mesa Geral',
      observacoes: obsItemModal.trim(),
      precoUnitarioFinal: precoUnitarioPreviewModal,
    };

    setCarrinho((prev) => [...prev, novoItem]);
    setProdutoCustomizando(null);
  };

  const valorTotalPedido = useMemo(() => {
    const soma = carrinho.reduce(
      (acc, item) =>
        roundBigDecimalHalfUp(
          acc + roundBigDecimalHalfUp(item.precoUnitarioFinal * item.quantidade)
        ),
      0
    );
    return roundBigDecimalHalfUp(soma);
  }, [carrinho]);

  const handleFinalizarPedido = async (e: React.FormEvent) => {
    e.preventDefault();
    if (carrinho.length === 0) return;
    setErrorMsg(null);
    setPedidoRecemCriado(null);

    const mesaNum = Math.max(1, parseInt(numeroMesa, 10) || 1);

    const itensPayload: ItemPedidoApiDTO[] = carrinho.map((item) => ({
      produtoId: item.produto.id,
      nomeProduto: item.produto.nome,
      quantidade: item.quantidade,
      precoTotalItem: roundBigDecimalHalfUp(
        item.precoUnitarioFinal * item.quantidade
      ),
      precoUnitario: roundBigDecimalHalfUp(item.precoUnitarioFinal),
      tamanhoEscolhido:
        item.varianteSelecionada?.tamanho ||
        item.varianteSelecionada?.nome ||
        null,
      tipoGraoEscolhido: item.tipoGraoEscolhido || null,
      adicionaisEscolhidos:
        item.adicionaisSelecionados.length > 0
          ? item.adicionaisSelecionados.map((a) => a.nome).join(', ')
          : null,
      pagadorItem: item.pagadorItem || 'Mesa Geral',
      observacoes: item.observacoes || null,
    }));

    const payload: PedidoRequestApiDTO = {
      numeroMesa: mesaNum,
      valorTotal: valorTotalPedido,
      itens: itensPayload,
    };

    try {
      const criado = await apiRequest<PedidoResponseApiDTO>({
        method: 'POST',
        url: '/pedidos',
        data: payload,
      });
      setPedidoRecemCriado(criado);
      setCarrinho([]);
    } catch (err) {
      setErrorMsg(parseApiError(err).message);
    }
  };

  const produtosFiltrados = useMemo(() => {
    return produtos.filter((p) => {
      const matchCat =
        categoriaFiltro === 'TODAS' || p.categoria === categoriaFiltro;
      const matchBusca =
        !busca.trim() ||
        p.nome.toLowerCase().includes(busca.toLowerCase()) ||
        (p.descricao || '').toLowerCase().includes(busca.toLowerCase());
      return matchCat && matchBusca;
    });
  }, [produtos, categoriaFiltro, busca]);

  return (
    <div className="space-y-6">
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

      {pedidoRecemCriado && (
        <div className="rounded-2xl bg-emerald-900 text-white p-5 shadow-lg flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div className="space-y-1">
            <div className="flex items-center gap-2 text-emerald-300 text-xs font-bold uppercase">
              <CheckCircle2 className="w-4 h-4" />
              <span>Pedido Registrado na API Real (MySQL cheirocafe_db)</span>
            </div>
            <h3 className="text-lg font-extrabold">
              Pedido #{pedidoRecemCriado.id} — Mesa {pedidoRecemCriado.numeroMesa}{' '}
              (Status: {pedidoRecemCriado.status})
            </h3>
            <p className="text-xs text-emerald-100">
              Valor Total (BigDecimal HALF_UP):{' '}
              {formatBigDecimalReais(pedidoRecemCriado.valorTotal)}
            </p>
          </div>
          <button
            type="button"
            onClick={() => navigate('/pedidos')}
            className="px-4 py-2 rounded-xl bg-white text-emerald-950 text-xs font-bold shrink-0 cursor-pointer"
          >
            Acompanhar no Monitor
          </button>
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Coluna Esquerda (7 cols): Grade de Produtos */}
        <div className="lg:col-span-7 space-y-4">
          <div className="bg-white rounded-2xl border border-slate-200 p-4 shadow-sm space-y-3">
            <div className="relative">
              <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
              <input
                type="text"
                value={busca}
                onChange={(e) => setBusca(e.target.value)}
                placeholder="Buscar produto no cardápio..."
                className="w-full pl-10 pr-4 py-2.5 rounded-xl border border-slate-300 text-xs focus:border-amber-600 outline-none"
              />
            </div>

            <div className="flex items-center gap-1.5 overflow-x-auto pb-1">
              {(
                ['TODAS', 'BEBIDAS', 'DOCES', 'SALGADOS'] as Array<
                  'TODAS' | CategoriaProdutoApi
                >
              ).map((cat) => (
                <button
                  key={cat}
                  type="button"
                  onClick={() => setCategoriaFiltro(cat)}
                  className={`px-3.5 py-1.5 rounded-xl text-xs font-bold whitespace-nowrap transition cursor-pointer ${
                    categoriaFiltro === cat
                      ? 'bg-slate-900 text-white'
                      : 'bg-slate-100 text-slate-700 hover:bg-slate-200'
                  }`}
                >
                  {cat === 'TODAS' ? `Todos (${produtos.length})` : cat}
                </button>
              ))}
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3.5">
            {produtosFiltrados.map((p) => {
              const ativo = p.ativo !== false;
              const menorPreco =
                p.variantes && p.variantes.length > 0
                  ? Math.min(...p.variantes.map((v) => Number(v.preco)))
                  : Number(p.precoBase || 0);

              return (
                <button
                  key={p.id}
                  type="button"
                  disabled={!ativo}
                  onClick={() => abrirModalProduto(p)}
                  className={`text-left p-4 rounded-2xl border transition flex flex-col justify-between gap-3 shadow-sm cursor-pointer ${
                    ativo
                      ? 'bg-white border-slate-200 hover:border-amber-500 hover:shadow-md'
                      : 'bg-rose-50/60 border-rose-200 opacity-75 cursor-not-allowed'
                  }`}
                >
                  <div>
                    <div className="flex items-center justify-between gap-2">
                      <span className="font-mono text-[10px] font-bold px-2 py-0.5 rounded bg-slate-100 text-slate-700">
                        {p.categoria}
                        {p.subcategoria ? ` · ${p.subcategoria}` : ''}
                      </span>
                      <span
                        className={`text-[10px] font-bold px-2 py-0.5 rounded ${
                          ativo
                            ? 'bg-emerald-100 text-emerald-800'
                            : 'bg-rose-600 text-white'
                        }`}
                      >
                        {ativo ? 'ATIVO' : 'INATIVO'}
                      </span>
                    </div>
                    <h3 className="text-sm font-bold text-slate-900 mt-2">
                      {p.nome}
                    </h3>
                    {p.descricao && (
                      <p className="text-xs text-slate-500 line-clamp-2 mt-0.5">
                        {p.descricao}
                      </p>
                    )}
                  </div>

                  <div className="flex items-center justify-between pt-2 border-t border-slate-100">
                    <span className="text-base font-extrabold text-amber-700 font-mono">
                      {p.variantes && p.variantes.length > 0 ? 'A partir de ' : ''}
                      {formatBigDecimalReais(menorPreco)}
                    </span>
                    <span className="text-xs font-bold text-slate-700 flex items-center gap-1">
                      <Plus className="w-3.5 h-3.5" />
                      <span>Adicionar</span>
                    </span>
                  </div>
                </button>
              );
            })}
          </div>
        </div>

        {/* Coluna Direita (5 cols): Comanda PDV */}
        <div className="lg:col-span-5">
          <form
            onSubmit={handleFinalizarPedido}
            className="bg-white rounded-2xl border border-slate-200 p-5 shadow-sm space-y-4 sticky top-20"
          >
            <div className="flex items-center justify-between border-b border-slate-200 pb-3">
              <div className="flex items-center gap-2">
                <ShoppingBag className="w-5 h-5 text-amber-600" />
                <h2 className="text-base font-extrabold text-slate-900">
                  Comanda Balcão / Mesa ({carrinho.length} item(ns))
                </h2>
              </div>
              {carrinho.length > 0 && (
                <button
                  type="button"
                  onClick={() => setCarrinho([])}
                  className="text-xs font-semibold text-rose-600 hover:underline cursor-pointer"
                >
                  Limpar
                </button>
              )}
            </div>

            <div>
              <label className="block text-[10px] font-bold uppercase text-slate-500 mb-1">
                Número da Mesa / Comanda *
              </label>
              <input
                type="number"
                min={1}
                required
                value={numeroMesa}
                onChange={(e) => setNumeroMesa(e.target.value)}
                className="w-full px-3 py-2 rounded-xl border border-slate-300 text-xs font-bold"
              />
            </div>

            {/* Itens do Carrinho */}
            <div className="space-y-2.5 max-h-72 overflow-y-auto pr-1">
              {carrinho.length === 0 ? (
                <p className="text-xs text-slate-400 text-center py-6">
                  Toque nos produtos ao lado para montar o pedido.
                </p>
              ) : (
                carrinho.map((item) => (
                  <div
                    key={item.localId}
                    className="p-3 rounded-xl bg-slate-50 border border-slate-200 text-xs space-y-1"
                  >
                    <div className="flex items-start justify-between gap-2">
                      <div>
                        <span className="font-bold text-slate-900">
                          {item.quantidade}x {item.produto.nome}
                        </span>
                        {item.varianteSelecionada && (
                          <span className="ml-1.5 px-1.5 py-0.5 rounded bg-indigo-100 text-indigo-800 text-[10px] font-bold">
                            {item.varianteSelecionada.tamanho}
                          </span>
                        )}
                        {item.tipoGraoEscolhido && (
                          <div className="text-[11px] text-amber-800">
                            Grão: {item.tipoGraoEscolhido}
                          </div>
                        )}
                        {item.adicionaisSelecionados.length > 0 && (
                          <div className="text-[11px] text-emerald-800">
                            +{' '}
                            {item.adicionaisSelecionados
                              .map((a) => a.nome)
                              .join(', ')}
                          </div>
                        )}
                        {item.pagadorItem && (
                          <div className="text-[10px] text-slate-500">
                            Pagador: {item.pagadorItem}
                          </div>
                        )}
                      </div>
                      <div className="flex items-center gap-2">
                        <strong className="text-slate-900 font-mono">
                          {formatBigDecimalReais(
                            roundBigDecimalHalfUp(
                              item.precoUnitarioFinal * item.quantidade
                            )
                          )}
                        </strong>
                        <button
                          type="button"
                          onClick={() =>
                            setCarrinho((prev) =>
                              prev.filter((x) => x.localId !== item.localId)
                            )
                          }
                          className="text-slate-400 hover:text-rose-600 cursor-pointer"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    </div>
                  </div>
                ))
              )}
            </div>

            <div className="pt-2 border-t border-slate-200 space-y-1.5 text-xs">
              <div className="flex justify-between text-base font-extrabold text-slate-900 pt-1">
                <span>Total do Pedido</span>
                <span className="text-amber-700 font-mono">
                  {formatBigDecimalReais(valorTotalPedido)}
                </span>
              </div>
            </div>

            <button
              type="submit"
              disabled={carrinho.length === 0}
              className="w-full py-3 rounded-xl bg-amber-600 hover:bg-amber-700 disabled:opacity-40 text-white text-sm font-bold flex items-center justify-center gap-2 shadow-md transition cursor-pointer"
            >
              <Utensils className="w-4 h-4" />
              <span>
                Enviar Pedido ({formatBigDecimalReais(valorTotalPedido)})
              </span>
            </button>
          </form>
        </div>
      </div>

      {/* Modal de Personalização de Item */}
      {produtoCustomizando && (
        <div className="fixed inset-0 z-40 bg-slate-950/60 backdrop-blur-sm flex items-center justify-center p-4 overflow-y-auto">
          <div className="bg-white rounded-3xl max-w-xl w-full p-6 space-y-5 shadow-2xl border border-slate-200">
            <div className="flex items-start justify-between border-b border-slate-200 pb-3">
              <div>
                <span className="text-xs font-mono font-bold text-amber-700">
                  {produtoCustomizando.categoria}
                  {produtoCustomizando.subcategoria
                    ? ` · ${produtoCustomizando.subcategoria}`
                    : ''}
                </span>
                <h3 className="text-lg font-extrabold text-slate-900">
                  {produtoCustomizando.nome}
                </h3>
                <p className="text-xs text-slate-500">
                  Preço Base:{' '}
                  {formatBigDecimalReais(produtoCustomizando.precoBase)}
                </p>
              </div>
              <button
                type="button"
                onClick={() => setProdutoCustomizando(null)}
                className="px-3 py-1 rounded-lg bg-slate-100 text-xs font-bold text-slate-700 cursor-pointer"
              >
                Cancelar
              </button>
            </div>

            {/* Seleção de Variante / Tamanho */}
            {produtoCustomizando.variantes &&
              produtoCustomizando.variantes.length > 0 && (
                <div className="space-y-2 bg-indigo-50/50 p-3.5 rounded-2xl border border-indigo-200">
                  <span className="text-xs font-bold text-indigo-950 block">
                    1. Escolha o Tamanho / Variante
                  </span>
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                    {produtoCustomizando.variantes.map((v, idx) => (
                      <button
                        key={v.id ?? idx}
                        type="button"
                        onClick={() => setVarianteIdxModal(idx)}
                        className={`p-2.5 rounded-xl border text-xs flex items-center justify-between cursor-pointer ${
                          varianteIdxModal === idx
                            ? 'bg-indigo-600 text-white border-indigo-600 font-bold'
                            : 'bg-white text-slate-700 border-slate-200'
                        }`}
                      >
                        <span>{v.tamanho}</span>
                        <span className="font-mono">
                          {formatBigDecimalReais(v.preco)}
                        </span>
                      </button>
                    ))}
                  </div>
                </div>
              )}

            {/* Seleção de Grão (Condicional para BEBIDAS) */}
            {produtoCustomizando.categoria === 'BEBIDAS' && (
              <div className="space-y-2 bg-amber-50/60 p-3.5 rounded-2xl border border-amber-200">
                <span className="text-xs font-bold text-amber-950 flex items-center gap-1.5">
                  <Coffee className="w-3.5 h-3.5 text-amber-700" />
                  <span>Tipo de Grão / Extração</span>
                </span>
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-2">
                  {OPCOES_GRAO.map((g) => (
                    <button
                      key={g}
                      type="button"
                      onClick={() => setGraoModal(g)}
                      className={`p-2 rounded-xl border text-[11px] font-bold cursor-pointer ${
                        graoModal === g
                          ? 'bg-amber-600 text-white border-amber-600'
                          : 'bg-white text-slate-700 border-slate-200'
                      }`}
                    >
                      {g}
                    </button>
                  ))}
                </div>
              </div>
            )}

            {/* Adicionais (Específicos + Globais da Categoria) */}
            {adicionaisDisponiveisParaModal.length > 0 && (
              <div className="space-y-2 bg-emerald-50/40 p-3.5 rounded-2xl border border-emerald-200">
                <span className="text-xs font-bold text-emerald-950 flex items-center gap-1.5">
                  <Sparkles className="w-3.5 h-3.5 text-emerald-700" />
                  <span>Adicionais Opcionais</span>
                </span>
                <div className="space-y-1.5 max-h-40 overflow-y-auto pr-1">
                  {adicionaisDisponiveisParaModal.map((adic, idx) => {
                    const key = `${adic.id ?? idx}-${adic.nome}`;
                    const checked = Boolean(adicionaisModalIds[key]);
                    return (
                      <label
                        key={key}
                        className={`flex items-center justify-between p-2 rounded-xl border text-xs cursor-pointer ${
                          checked
                            ? 'bg-emerald-100/70 border-emerald-500 font-bold'
                            : 'bg-white border-slate-200'
                        }`}
                      >
                        <div className="flex items-center gap-2">
                          <input
                            type="checkbox"
                            checked={checked}
                            onChange={(e) =>
                              setAdicionaisModalIds((prev) => ({
                                ...prev,
                                [key]: e.target.checked,
                              }))
                            }
                          />
                          <span>{adic.nome}</span>
                          <span className="text-[9px] font-mono px-1.5 py-0.5 rounded bg-slate-100 text-slate-600">
                            {adic.escopo || 'PRODUTO'}
                          </span>
                        </div>
                        <span className="text-emerald-800 font-mono font-bold">
                          +{formatBigDecimalReais(adic.preco)}
                        </span>
                      </label>
                    );
                  })}
                </div>
              </div>
            )}

            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 items-end">
              <div>
                <label className="block text-[10px] font-bold uppercase text-slate-500 mb-1">
                  Quantidade
                </label>
                <input
                  type="number"
                  min={1}
                  max={99}
                  value={qtdModal}
                  onChange={(e) =>
                    setQtdModal(Math.max(1, Number(e.target.value)))
                  }
                  className="w-full px-3 py-2 rounded-xl border border-slate-300 text-sm font-bold"
                />
              </div>
              <div>
                <label className="block text-[10px] font-bold uppercase text-slate-500 mb-1">
                  Pagador na Mesa
                </label>
                <div className="relative">
                  <User className="w-3.5 h-3.5 text-slate-400 absolute left-2.5 top-1/2 -translate-y-1/2" />
                  <input
                    type="text"
                    value={pagadorModal}
                    onChange={(e) => setPagadorModal(e.target.value)}
                    placeholder="Ex: Cliente 1"
                    className="w-full pl-8 pr-2.5 py-2 rounded-xl border border-slate-300 text-xs"
                  />
                </div>
              </div>
              <div>
                <label className="block text-[10px] font-bold uppercase text-slate-500 mb-1">
                  Observações
                </label>
                <input
                  type="text"
                  value={obsItemModal}
                  onChange={(e) => setObsItemModal(e.target.value)}
                  placeholder="Ex: Sem açúcar"
                  className="w-full px-3 py-2 rounded-xl border border-slate-300 text-xs"
                />
              </div>
            </div>

            <button
              type="button"
              onClick={confirmarItemNoCarrinho}
              className="w-full py-3 rounded-xl bg-amber-600 hover:bg-amber-700 text-white text-sm font-bold shadow-md cursor-pointer"
            >
              Adicionar à Comanda (
              {formatBigDecimalReais(
                roundBigDecimalHalfUp(precoUnitarioPreviewModal * qtdModal)
              )}
              )
            </button>
          </div>
        </div>
      )}
    </div>
  );
};
