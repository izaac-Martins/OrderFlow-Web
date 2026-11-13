import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  ShoppingBag,
  Plus,
  Minus,
  Trash2,
  CheckCircle2,
  AlertTriangle,
  Search,
  Banknote,
  CreditCard,
  QrCode,
  Utensils,
} from 'lucide-react';
import { apiRequest } from '../lib/http';
import { parseApiError } from '../lib/errors';
import {
  calcPercentualHalfEven,
  formatCentavos,
  parseReaisToCentavos,
} from '../lib/formatters';
import { stompManager } from '../lib/stompClient';
import { useAuth } from '../context/AuthContext';
import type {
  Categoria,
  MetodoPagamento,
  ModalidadePagamento,
  ModificadorSelecionadoRequest,
  PedidoResponse,
  Produto,
  SelecaoComboSlotRequest,
  SessaoCaixaGerencial,
  TipoAtendimento,
} from '../types/api';

interface CarrinhoItem {
  localId: string;
  produto: Produto;
  quantidade: number;
  observacao: string;
  modificadores: Array<{
    modificadorId: string;
    nome: string;
    quantidade: number;
    adicionalUnitarioCentavos: number;
  }>;
  selecoesCombo: Array<{
    slotId: string;
    slotNome: string;
    produtoId: string;
    produtoNome: string;
    acrescimoCentavos: number;
  }>;
  unitarioFinalCentavos: number;
}

export const PdvPage: React.FC = () => {
  const { user } = useAuth();
  const navigate = useNavigate();

  const [categorias, setCategorias] = useState<Categoria[]>([]);
  const [produtos, setProdutos] = useState<Produto[]>([]);
  const [sessaoAberta, setSessaoAberta] =
    useState<SessaoCaixaGerencial | null>(null);
  const [categoriaFiltro, setCategoriaFiltro] = useState<string>('TODAS');
  const [busca, setBusca] = useState('');

  // Carrinho e Configuração do Pedido
  const [carrinho, setCarrinho] = useState<CarrinhoItem[]>([]);
  const [tipoAtendimento, setTipoAtendimento] =
    useState<TipoAtendimento>('BALCAO');
  const [modalidadePagamento, setModalidadePagamento] =
    useState<ModalidadePagamento>('PRE_PAGO');
  const [identificadorCliente, setIdentificadorCliente] =
    useState('Cliente Balcão');
  const [mesa, setMesa] = useState('');
  const [observacaoPedido, setObservacaoPedido] = useState('');
  const [descontoReais, setDescontoReais] = useState('0,00');

  // Pagamento Inicial (para Pré-Pago)
  const [metodoPagamento, setMetodoPagamento] =
    useState<MetodoPagamento>('DINHEIRO');
  const [valorRecebidoReais, setValorRecebidoReais] = useState('50,00');

  // Modal de Personalização de Item (Modificadores / Combo Slots)
  const [produtoCustomizando, setProdutoCustomizando] =
    useState<Produto | null>(null);
  const [qtdModal, setQtdModal] = useState(1);
  const [obsItemModal, setObsItemModal] = useState('');
  const [modsSelecionadosModal, setModsSelecionadosModal] = useState<
    Record<string, number>
  >({});
  const [slotsSelecionadosModal, setSlotsSelecionadosModal] = useState<
    Record<string, string>
  >({});

  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [pedidoRecemCriado, setPedidoRecemCriado] =
    useState<PedidoResponse | null>(null);

  const carregarCatalogoESessao = useCallback(async () => {
    try {
      const [cats, prods, sessoes] = await Promise.all([
        apiRequest<Categoria[]>({ method: 'GET', url: '/categorias' }),
        apiRequest<Produto[]>({ method: 'GET', url: '/produtos' }),
        apiRequest<SessaoCaixaGerencial[]>({
          method: 'GET',
          url: '/caixa/sessoes',
        }),
      ]);
      setCategorias(cats);
      setProdutos(prods);
      setSessaoAberta(sessoes.find((s) => s.status === 'ABERTA') || null);
    } catch (err) {
      setErrorMsg(parseApiError(err).message);
    }
  }, []);

  useEffect(() => {
    carregarCatalogoESessao();
  }, [carregarCatalogoESessao]);

  useEffect(() => {
    const lojaId = user?.lojaId || '019325f0-8a00-7000-8000-000000000001';
    return stompManager.subscribe(
      `/topic/lojas/${lojaId}/disponibilidade`,
      () => {
        carregarCatalogoESessao();
      }
    );
  }, [user?.lojaId, carregarCatalogoESessao]);

  const abrirModalProduto = (prod: Produto) => {
    if (!prod.disponivel) return;
    setErrorMsg(null);
    setProdutoCustomizando(prod);
    setQtdModal(1);
    setObsItemModal('');

    // Pré-seleciona modificadores padrão
    const padroes: Record<string, number> = {};
    for (const v of prod.gruposModificadores || []) {
      for (const m of v.grupo.modificadores) {
        if (m.padrao && m.disponivel) {
          padroes[m.id] = 1;
        }
      }
    }
    setModsSelecionadosModal(padroes);

    // Pré-seleciona opções padrão de cada slot de combo
    const slotsPadrao: Record<string, string> = {};
    for (const slot of prod.comboSlots || []) {
      const opPadrao =
        slot.opcoes.find((o) => o.padrao && o.disponivel) ||
        slot.opcoes.find((o) => o.disponivel);
      if (opPadrao) {
        slotsPadrao[slot.id] = opPadrao.produtoId;
      }
    }
    setSlotsSelecionadosModal(slotsPadrao);
  };

  const precoUnitarioPreviewModal = useMemo(() => {
    if (!produtoCustomizando) return 0;
    let totalUnit = produtoCustomizando.precoBaseCentavos;

    if (produtoCustomizando.tipo === 'SIMPLES') {
      for (const v of produtoCustomizando.gruposModificadores || []) {
        for (const m of v.grupo.modificadores) {
          const qtd = modsSelecionadosModal[m.id] || 0;
          if (qtd > 0) {
            if (m.tipoPreco === 'FIXO') {
              totalUnit += m.valorPreco * qtd;
            } else if (m.tipoPreco === 'PERCENTUAL') {
              totalUnit +=
                calcPercentualHalfEven(
                  produtoCustomizando.precoBaseCentavos,
                  m.valorPreco
                ) * qtd;
            }
          }
        }
      }
    } else {
      for (const slot of produtoCustomizando.comboSlots || []) {
        const chosenProdId = slotsSelecionadosModal[slot.id];
        const op = slot.opcoes.find((o) => o.produtoId === chosenProdId);
        if (op) {
          totalUnit += op.acrescimoCentavos;
        }
      }
    }
    return totalUnit;
  }, [produtoCustomizando, modsSelecionadosModal, slotsSelecionadosModal]);

  const confirmarItemNoCarrinho = () => {
    if (!produtoCustomizando) return;
    setErrorMsg(null);

    // Valida grupos obrigatórios
    if (produtoCustomizando.tipo === 'SIMPLES') {
      for (const v of produtoCustomizando.gruposModificadores || []) {
        const totalGrupo = v.grupo.modificadores.reduce(
          (acc, m) => acc + (modsSelecionadosModal[m.id] || 0),
          0
        );
        if (totalGrupo < v.minSelecaoEfetiva) {
          setErrorMsg(
            `O grupo obrigatório '${v.grupo.nome}' exige pelo menos ${v.minSelecaoEfetiva} seleção(ões).`
          );
          return;
        }
        if (totalGrupo > v.maxSelecaoEfetiva) {
          setErrorMsg(
            `O grupo '${v.grupo.nome}' permite no máximo ${v.maxSelecaoEfetiva} seleção(ões).`
          );
          return;
        }
      }
    } else {
      for (const slot of produtoCustomizando.comboSlots || []) {
        if (slot.minSelecao >= 1 && !slotsSelecionadosModal[slot.id]) {
          setErrorMsg(`Selecione uma opção para o slot '${slot.nome}'.`);
          return;
        }
      }
    }

    const modsArray: CarrinhoItem['modificadores'] = [];
    for (const v of produtoCustomizando.gruposModificadores || []) {
      for (const m of v.grupo.modificadores) {
        const qtd = modsSelecionadosModal[m.id] || 0;
        if (qtd > 0) {
          const adic =
            m.tipoPreco === 'FIXO'
              ? m.valorPreco * qtd
              : m.tipoPreco === 'PERCENTUAL'
              ? calcPercentualHalfEven(
                  produtoCustomizando.precoBaseCentavos,
                  m.valorPreco
                ) * qtd
              : 0;
          modsArray.push({
            modificadorId: m.id,
            nome: m.nome,
            quantidade: qtd,
            adicionalUnitarioCentavos: adic,
          });
        }
      }
    }

    const selecoesComboArray: CarrinhoItem['selecoesCombo'] = [];
    for (const slot of produtoCustomizando.comboSlots || []) {
      const pid = slotsSelecionadosModal[slot.id];
      const op = slot.opcoes.find((o) => o.produtoId === pid);
      if (op) {
        selecoesComboArray.push({
          slotId: slot.id,
          slotNome: slot.nome,
          produtoId: op.produtoId,
          produtoNome: op.produtoNome,
          acrescimoCentavos: op.acrescimoCentavos,
        });
      }
    }

    const novoItem: CarrinhoItem = {
      localId: `${produtoCustomizando.id}-${Date.now()}`,
      produto: produtoCustomizando,
      quantidade: qtdModal,
      observacao: obsItemModal,
      modificadores: modsArray,
      selecoesCombo: selecoesComboArray,
      unitarioFinalCentavos: precoUnitarioPreviewModal,
    };

    setCarrinho((prev) => [...prev, novoItem]);
    setProdutoCustomizando(null);
  };

  const subtotalCentavos = useMemo(
    () =>
      carrinho.reduce(
        (acc, i) => acc + i.unitarioFinalCentavos * i.quantidade,
        0
      ),
    [carrinho]
  );

  const descontoCentavos = useMemo(
    () => Math.max(0, parseReaisToCentavos(descontoReais)),
    [descontoReais]
  );

  const totalPedidoCentavos = Math.max(0, subtotalCentavos - descontoCentavos);
  const valorRecebidoCentavos = parseReaisToCentavos(valorRecebidoReais);
  const trocoEstimadoCentavos =
    metodoPagamento === 'DINHEIRO'
      ? Math.max(0, valorRecebidoCentavos - totalPedidoCentavos)
      : 0;

  const handleFinalizarPedido = async (e: React.FormEvent) => {
    e.preventDefault();
    if (carrinho.length === 0) return;
    setErrorMsg(null);
    setPedidoRecemCriado(null);

    const itensPayload = carrinho.map((item) => ({
      produtoId: item.produto.id,
      quantidade: item.quantidade,
      observacao: item.observacao || undefined,
      modificadores: item.modificadores.map(
        (m): ModificadorSelecionadoRequest => ({
          modificadorId: m.modificadorId,
          quantidade: m.quantidade,
        })
      ),
      selecoesCombo: item.selecoesCombo.map(
        (s): SelecaoComboSlotRequest => ({
          slotId: s.slotId,
          produtoId: s.produtoId,
        })
      ),
    }));

    try {
      const criado = await apiRequest<PedidoResponse>({
        method: 'POST',
        url: '/pedidos',
        data: {
          origem: 'MASTER_BALCAO',
          tipoAtendimento,
          modalidadePagamento,
          sessaoCaixaId: sessaoAberta?.id,
          identificadorCliente,
          mesa: tipoAtendimento === 'MESA' ? mesa || '01' : undefined,
          observacao: observacaoPedido || undefined,
          descontoCentavos,
          totalEsperadoCentavos: totalPedidoCentavos,
          pagamentoInicial:
            modalidadePagamento === 'PRE_PAGO'
              ? {
                  sessaoCaixaId: sessaoAberta?.id,
                  metodo: metodoPagamento,
                  valorCentavos: totalPedidoCentavos,
                  valorRecebidoCentavos:
                    metodoPagamento === 'DINHEIRO'
                      ? Math.max(totalPedidoCentavos, valorRecebidoCentavos)
                      : undefined,
                }
              : undefined,
          itens: itensPayload,
        },
      });
      setPedidoRecemCriado(criado);
      setCarrinho([]);
      setObservacaoPedido('');
    } catch (err) {
      setErrorMsg(parseApiError(err).message);
    }
  };

  const produtosFiltrados = produtos.filter((p) => {
    const matchCat =
      categoriaFiltro === 'TODAS' || p.categoriaId === categoriaFiltro;
    const matchBusca =
      !busca.trim() ||
      p.nome.toLowerCase().includes(busca.toLowerCase()) ||
      p.sku.toLowerCase().includes(busca.toLowerCase());
    return matchCat && matchBusca;
  });

  return (
    <div className="space-y-6">
      {/* Alerta se não houver sessão de caixa aberta */}
      {!sessaoAberta && (
        <div className="rounded-2xl bg-amber-50 border border-amber-300 p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div className="flex items-center gap-2.5 text-xs font-semibold text-amber-900">
            <AlertTriangle className="w-5 h-5 text-amber-600 shrink-0" />
            <span>
              Nenhuma sessão de caixa aberta no momento. Pedidos{' '}
              <strong>PRÉ-PAGOS</strong> exigem sessão aberta para registrar o
              recebimento; ou abra o caixa em <strong>Operação de Caixa</strong>.
            </span>
          </div>
          <button
            type="button"
            onClick={() => navigate('/caixa')}
            className="px-4 py-2 rounded-xl bg-amber-600 hover:bg-amber-700 text-white text-xs font-bold shrink-0"
          >
            Abrir Sessão de Caixa
          </button>
        </div>
      )}

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
              <span>Pedido Emitido e Enviado ao KDS via STOMP</span>
            </div>
            <h3 className="text-lg font-extrabold">
              Senha #{pedidoRecemCriado.numeroDiario} —{' '}
              {pedidoRecemCriado.identificadorCliente} (Status:{' '}
              {pedidoRecemCriado.status})
            </h3>
            <p className="text-xs text-emerald-100">
              Total: {formatCentavos(pedidoRecemCriado.valorTotalCentavos)}
              {pedidoRecemCriado.pagamentos[0]?.trocoCentavos
                ? ` · Troco em espécie: ${formatCentavos(
                    pedidoRecemCriado.pagamentos[0].trocoCentavos
                  )}`
                : ''}
            </p>
          </div>
          <button
            type="button"
            onClick={() => navigate('/pedidos')}
            className="px-4 py-2 rounded-xl bg-white text-emerald-950 text-xs font-bold shrink-0"
          >
            Acompanhar no Monitor
          </button>
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Coluna Esquerda (7 cols): Grade de Produtos do PDV */}
        <div className="lg:col-span-7 space-y-4">
          <div className="bg-white rounded-2xl border border-slate-200 p-4 shadow-sm space-y-3">
            <div className="relative">
              <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
              <input
                type="text"
                value={busca}
                onChange={(e) => setBusca(e.target.value)}
                placeholder="Buscar produto por nome ou código SKU..."
                className="w-full pl-10 pr-4 py-2.5 rounded-xl border border-slate-300 text-xs focus:border-amber-600 outline-none"
              />
            </div>

            <div className="flex items-center gap-1.5 overflow-x-auto pb-1">
              <button
                type="button"
                onClick={() => setCategoriaFiltro('TODAS')}
                className={`px-3 py-1.5 rounded-xl text-xs font-bold whitespace-nowrap transition ${
                  categoriaFiltro === 'TODAS'
                    ? 'bg-slate-900 text-white'
                    : 'bg-slate-100 text-slate-700 hover:bg-slate-200'
                }`}
              >
                Todos ({produtos.length})
              </button>
              {categorias.map((c) => (
                <button
                  key={c.id}
                  type="button"
                  onClick={() => setCategoriaFiltro(c.id)}
                  className={`px-3 py-1.5 rounded-xl text-xs font-bold whitespace-nowrap transition ${
                    categoriaFiltro === c.id
                      ? 'bg-amber-600 text-white'
                      : 'bg-slate-100 text-slate-700 hover:bg-slate-200'
                  }`}
                >
                  {c.nome}
                </button>
              ))}
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3.5">
            {produtosFiltrados.map((p) => (
              <button
                key={p.id}
                type="button"
                disabled={!p.disponivel}
                onClick={() => abrirModalProduto(p)}
                className={`text-left p-4 rounded-2xl border transition flex flex-col justify-between gap-3 shadow-sm ${
                  p.disponivel
                    ? 'bg-white border-slate-200 hover:border-amber-500 hover:shadow-md'
                    : 'bg-rose-50/60 border-rose-200 opacity-75 cursor-not-allowed'
                }`}
              >
                <div>
                  <div className="flex items-center justify-between gap-2">
                    <span className="font-mono text-[10px] font-bold px-2 py-0.5 rounded bg-slate-100 text-slate-700">
                      {p.sku} · {p.tipo}
                    </span>
                    <span
                      className={`text-[10px] font-bold px-2 py-0.5 rounded ${
                        p.disponivel
                          ? 'bg-emerald-100 text-emerald-800'
                          : 'bg-rose-600 text-white'
                      }`}
                    >
                      {p.disponivel ? p.estacaoPreparoEfetiva : 'ESGOTADO (86)'}
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
                  {!p.disponivel && p.motivoIndisponibilidade && (
                    <p className="text-[11px] font-semibold text-rose-700 mt-1">
                      {p.motivoIndisponibilidade}
                    </p>
                  )}
                </div>

                <div className="flex items-center justify-between pt-2 border-t border-slate-100">
                  <span className="text-base font-extrabold text-amber-700">
                    {formatCentavos(p.precoBaseCentavos)}
                  </span>
                  <span className="text-xs font-bold text-slate-700 flex items-center gap-1">
                    <Plus className="w-3.5 h-3.5" />
                    <span>Selecionar</span>
                  </span>
                </div>
              </button>
            ))}
          </div>
        </div>

        {/* Coluna Direita (5 cols): Comanda / Carrinho & Checkout */}
        <div className="lg:col-span-5">
          <form
            onSubmit={handleFinalizarPedido}
            className="bg-white rounded-2xl border border-slate-200 p-5 shadow-sm space-y-4 sticky top-20"
          >
            <div className="flex items-center justify-between border-b border-slate-200 pb-3">
              <div className="flex items-center gap-2">
                <ShoppingBag className="w-5 h-5 text-amber-600" />
                <h2 className="text-base font-extrabold text-slate-900">
                  Comanda PDV ({carrinho.length} item(ns))
                </h2>
              </div>
              {carrinho.length > 0 && (
                <button
                  type="button"
                  onClick={() => setCarrinho([])}
                  className="text-xs font-semibold text-rose-600 hover:underline"
                >
                  Limpar
                </button>
              )}
            </div>

            {/* Itens do Carrinho */}
            <div className="space-y-2.5 max-h-60 overflow-y-auto pr-1">
              {carrinho.length === 0 ? (
                <p className="text-xs text-slate-400 text-center py-6">
                  Toque nos produtos ao lado para montar o pedido.
                </p>
              ) : (
                carrinho.map((item) => (
                  <div
                    key={item.localId}
                    className="p-3 rounded-xl bg-slate-50 border border-slate-200 text-xs space-y-1.5"
                  >
                    <div className="flex items-start justify-between gap-2">
                      <div>
                        <span className="font-bold text-slate-900">
                          {item.quantidade}x {item.produto.nome}
                        </span>
                        {item.modificadores.length > 0 && (
                          <div className="text-[11px] text-slate-600 mt-0.5">
                            +{' '}
                            {item.modificadores
                              .map((m) => `${m.quantidade}x ${m.nome}`)
                              .join(', ')}
                          </div>
                        )}
                        {item.selecoesCombo.length > 0 && (
                          <div className="text-[11px] text-purple-800 mt-0.5">
                            Combo:{' '}
                            {item.selecoesCombo
                              .map((s) => `${s.slotNome}: ${s.produtoNome}`)
                              .join(' | ')}
                          </div>
                        )}
                      </div>
                      <div className="flex items-center gap-2">
                        <strong className="text-slate-900">
                          {formatCentavos(
                            item.unitarioFinalCentavos * item.quantidade
                          )}
                        </strong>
                        <button
                          type="button"
                          onClick={() =>
                            setCarrinho((prev) =>
                              prev.filter((x) => x.localId !== item.localId)
                            )
                          }
                          className="text-slate-400 hover:text-rose-600"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    </div>
                  </div>
                ))
              )}
            </div>

            {/* Atendimento & Modalidade */}
            <div className="grid grid-cols-3 gap-2 pt-2 border-t border-slate-200">
              {(['BALCAO', 'MESA', 'DELIVERY'] as TipoAtendimento[]).map(
                (t) => (
                  <button
                    key={t}
                    type="button"
                    onClick={() => {
                      setTipoAtendimento(t);
                      if (t === 'MESA') setModalidadePagamento('POS_PAGO');
                      else setModalidadePagamento('PRE_PAGO');
                    }}
                    className={`py-2 rounded-xl text-xs font-bold border transition ${
                      tipoAtendimento === t
                        ? 'bg-slate-900 text-white border-slate-900'
                        : 'bg-white text-slate-700 border-slate-300'
                    }`}
                  >
                    {t}
                  </button>
                )
              )}
            </div>

            <div className="grid grid-cols-2 gap-2">
              <button
                type="button"
                onClick={() => setModalidadePagamento('PRE_PAGO')}
                className={`py-2 rounded-xl text-xs font-bold border ${
                  modalidadePagamento === 'PRE_PAGO'
                    ? 'bg-amber-600 text-white border-amber-600'
                    : 'bg-white text-slate-700 border-slate-300'
                }`}
              >
                PRÉ-PAGO (Quitar Agora)
              </button>
              <button
                type="button"
                onClick={() => setModalidadePagamento('POS_PAGO')}
                className={`py-2 rounded-xl text-xs font-bold border ${
                  modalidadePagamento === 'POS_PAGO'
                    ? 'bg-amber-600 text-white border-amber-600'
                    : 'bg-white text-slate-700 border-slate-300'
                }`}
              >
                PÓS-PAGO (Comanda/Mesa)
              </button>
            </div>

            <div className="grid grid-cols-3 gap-2">
              <div className="col-span-2">
                <label className="block text-[10px] font-bold uppercase text-slate-500 mb-0.5">
                  Identificador Cliente
                </label>
                <input
                  type="text"
                  required
                  value={identificadorCliente}
                  onChange={(e) => setIdentificadorCliente(e.target.value)}
                  className="w-full px-3 py-1.5 rounded-lg border border-slate-300 text-xs"
                />
              </div>
              <div>
                <label className="block text-[10px] font-bold uppercase text-slate-500 mb-0.5">
                  Mesa / Senha
                </label>
                <input
                  type="text"
                  value={mesa}
                  onChange={(e) => setMesa(e.target.value)}
                  placeholder="Ex.: 04"
                  className="w-full px-3 py-1.5 rounded-lg border border-slate-300 text-xs"
                />
              </div>
            </div>

            {/* Pagamento quando PRÉ-PAGO */}
            {modalidadePagamento === 'PRE_PAGO' && (
              <div className="p-3.5 rounded-xl bg-slate-50 border border-slate-200 space-y-3">
                <span className="text-[11px] font-bold uppercase text-slate-600 block">
                  Forma de Recebimento
                </span>
                <div className="grid grid-cols-3 gap-1.5">
                  {[
                    { m: 'DINHEIRO' as MetodoPagamento, l: 'Dinheiro', icon: Banknote },
                    { m: 'PIX' as MetodoPagamento, l: 'PIX', icon: QrCode },
                    {
                      m: 'CARTAO_CREDITO' as MetodoPagamento,
                      l: 'Crédito',
                      icon: CreditCard,
                    },
                  ].map((item) => {
                    const Icon = item.icon;
                    return (
                      <button
                        key={item.m}
                        type="button"
                        onClick={() => setMetodoPagamento(item.m)}
                        className={`py-2 px-2 rounded-lg text-xs font-bold flex items-center justify-center gap-1 border ${
                          metodoPagamento === item.m
                            ? 'bg-emerald-600 text-white border-emerald-600'
                            : 'bg-white text-slate-700 border-slate-300'
                        }`}
                      >
                        <Icon className="w-3.5 h-3.5" />
                        <span>{item.l}</span>
                      </button>
                    );
                  })}
                </div>

                {metodoPagamento === 'DINHEIRO' && (
                  <div className="grid grid-cols-2 gap-3 pt-1">
                    <div>
                      <label className="block text-[10px] font-bold uppercase text-slate-500 mb-0.5">
                        Recebido em Espécie (R$)
                      </label>
                      <input
                        type="text"
                        value={valorRecebidoReais}
                        onChange={(e) => setValorRecebidoReais(e.target.value)}
                        className="w-full px-2.5 py-1.5 rounded-lg border border-slate-300 text-xs font-bold bg-white"
                      />
                    </div>
                    <div>
                      <span className="block text-[10px] font-bold uppercase text-slate-500 mb-0.5">
                        Troco Calculado
                      </span>
                      <div className="px-2.5 py-1.5 rounded-lg bg-emerald-100 text-emerald-900 font-extrabold text-xs">
                        {formatCentavos(trocoEstimadoCentavos)}
                      </div>
                    </div>
                  </div>
                )}
              </div>
            )}

            {/* Resumo Financeiro em Centavos */}
            <div className="pt-2 border-t border-slate-200 space-y-1.5 text-xs">
              <div className="flex justify-between text-slate-600">
                <span>Subtotal dos Itens</span>
                <span>{formatCentavos(subtotalCentavos)}</span>
              </div>
              <div className="flex items-center justify-between text-slate-600">
                <span>Desconto (R$)</span>
                <input
                  type="text"
                  value={descontoReais}
                  onChange={(e) => setDescontoReais(e.target.value)}
                  className="w-20 text-right px-2 py-0.5 rounded border border-slate-300 text-xs"
                />
              </div>
              <div className="flex justify-between text-base font-extrabold text-slate-900 pt-1">
                <span>Total a Pagar</span>
                <span className="text-amber-700">
                  {formatCentavos(totalPedidoCentavos)}
                </span>
              </div>
            </div>

            <button
              type="submit"
              disabled={carrinho.length === 0}
              className="w-full py-3 rounded-xl bg-amber-600 hover:bg-amber-700 disabled:opacity-40 text-white text-sm font-bold flex items-center justify-center gap-2 shadow-md transition"
            >
              <Utensils className="w-4 h-4" />
              <span>
                Emitir Pedido ({formatCentavos(totalPedidoCentavos)})
              </span>
            </button>
          </form>
        </div>
      </div>

      {/* Modal de Personalização de Item (Modificadores e Slots de Combo) */}
      {produtoCustomizando && (
        <div className="fixed inset-0 z-40 bg-slate-950/60 backdrop-blur-sm flex items-center justify-center p-4 overflow-y-auto">
          <div className="bg-white rounded-3xl max-w-xl w-full p-6 space-y-5 shadow-2xl border border-slate-200">
            <div className="flex items-start justify-between border-b border-slate-200 pb-3">
              <div>
                <span className="text-xs font-mono font-bold text-amber-700">
                  {produtoCustomizando.sku} · {produtoCustomizando.tipo}
                </span>
                <h3 className="text-lg font-extrabold text-slate-900">
                  {produtoCustomizando.nome}
                </h3>
                <p className="text-xs text-slate-500">
                  Base: {formatCentavos(produtoCustomizando.precoBaseCentavos)}
                </p>
              </div>
              <button
                type="button"
                onClick={() => setProdutoCustomizando(null)}
                className="px-3 py-1 rounded-lg bg-slate-100 text-xs font-bold text-slate-700"
              >
                Cancelar
              </button>
            </div>

            {/* Grupos de Modificadores para produto SIMPLES */}
            {produtoCustomizando.tipo === 'SIMPLES' &&
              (produtoCustomizando.gruposModificadores || []).map((vinculo) => (
                <div
                  key={vinculo.grupo.id}
                  className="space-y-2 bg-slate-50 p-3.5 rounded-2xl border border-slate-200"
                >
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-bold text-slate-900">
                      {vinculo.grupo.nome}
                    </span>
                    <span className="text-[10px] font-bold px-2 py-0.5 rounded bg-amber-100 text-amber-900">
                      mín {vinculo.minSelecaoEfetiva} / máx{' '}
                      {vinculo.maxSelecaoEfetiva}
                    </span>
                  </div>
                  <div className="space-y-1.5">
                    {vinculo.grupo.modificadores.map((mod) => {
                      const qtdAtual = modsSelecionadosModal[mod.id] || 0;
                      const adicLabel =
                        mod.tipoPreco === 'FIXO'
                          ? `+${formatCentavos(mod.valorPreco)}`
                          : mod.tipoPreco === 'PERCENTUAL'
                          ? `+${(mod.valorPreco / 100).toFixed(0)}% (${formatCentavos(
                              calcPercentualHalfEven(
                                produtoCustomizando.precoBaseCentavos,
                                mod.valorPreco
                              )
                            )})`
                          : 'Incluso';

                      return (
                        <div
                          key={mod.id}
                          className={`flex items-center justify-between p-2 rounded-xl border text-xs ${
                            !mod.disponivel
                              ? 'bg-rose-50 border-rose-200 opacity-60'
                              : qtdAtual > 0
                              ? 'bg-amber-50 border-amber-400'
                              : 'bg-white border-slate-200'
                          }`}
                        >
                          <div>
                            <span className="font-semibold text-slate-800">
                              {mod.nome}
                            </span>
                            <span className="ml-2 text-[11px] text-amber-700 font-bold">
                              {adicLabel}
                            </span>
                          </div>
                          {mod.disponivel ? (
                            <div className="flex items-center gap-2">
                              <button
                                type="button"
                                onClick={() =>
                                  setModsSelecionadosModal((prev) => ({
                                    ...prev,
                                    [mod.id]: Math.max(
                                      0,
                                      (prev[mod.id] || 0) - 1
                                    ),
                                  }))
                                }
                                className="w-6 h-6 rounded-lg bg-slate-200 flex items-center justify-center"
                              >
                                <Minus className="w-3 h-3" />
                              </button>
                              <span className="font-bold w-4 text-center">
                                {qtdAtual}
                              </span>
                              <button
                                type="button"
                                onClick={() =>
                                  setModsSelecionadosModal((prev) => ({
                                    ...prev,
                                    [mod.id]: Math.min(
                                      mod.maxQuantidade,
                                      (prev[mod.id] || 0) + 1
                                    ),
                                  }))
                                }
                                className="w-6 h-6 rounded-lg bg-amber-600 text-white flex items-center justify-center"
                              >
                                <Plus className="w-3 h-3" />
                              </button>
                            </div>
                          ) : (
                            <span className="text-[10px] font-bold text-rose-700">
                              ESGOTADO
                            </span>
                          )}
                        </div>
                      );
                    })}
                  </div>
                </div>
              ))}

            {/* Slots para produto COMBO */}
            {produtoCustomizando.tipo === 'COMBO' &&
              (produtoCustomizando.comboSlots || []).map((slot) => (
                <div
                  key={slot.id}
                  className="space-y-2 bg-purple-50/50 p-3.5 rounded-2xl border border-purple-200"
                >
                  <span className="text-xs font-bold text-purple-950 block">
                    {slot.nome} (Obrigatório)
                  </span>
                  <div className="space-y-1.5">
                    {slot.opcoes.map((op) => (
                      <label
                        key={op.produtoId}
                        className={`flex items-center justify-between p-2.5 rounded-xl border text-xs cursor-pointer ${
                          slotsSelecionadosModal[slot.id] === op.produtoId
                            ? 'bg-white border-purple-600 font-bold'
                            : 'bg-white/70 border-slate-200'
                        }`}
                      >
                        <div className="flex items-center gap-2">
                          <input
                            type="radio"
                            name={slot.id}
                            disabled={!op.disponivel}
                            checked={
                              slotsSelecionadosModal[slot.id] === op.produtoId
                            }
                            onChange={() =>
                              setSlotsSelecionadosModal((prev) => ({
                                ...prev,
                                [slot.id]: op.produtoId,
                              }))
                            }
                          />
                          <span>{op.produtoNome}</span>
                        </div>
                        <span className="text-purple-800 font-bold">
                          {op.acrescimoCentavos > 0
                            ? `+${formatCentavos(op.acrescimoCentavos)}`
                            : 'Incluso'}
                        </span>
                      </label>
                    ))}
                  </div>
                </div>
              ))}

            <div className="grid grid-cols-3 gap-3 items-end">
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
              <div className="col-span-2">
                <label className="block text-[10px] font-bold uppercase text-slate-500 mb-1">
                  Observação do Item (Bar/Cozinha)
                </label>
                <input
                  type="text"
                  value={obsItemModal}
                  onChange={(e) => setObsItemModal(e.target.value)}
                  placeholder="Ex.: Sem canela / Bem quente"
                  className="w-full px-3 py-2 rounded-xl border border-slate-300 text-xs"
                />
              </div>
            </div>

            <button
              type="button"
              onClick={confirmarItemNoCarrinho}
              className="w-full py-3 rounded-xl bg-amber-600 hover:bg-amber-700 text-white text-sm font-bold shadow-md"
            >
              Adicionar à Comanda (
              {formatCentavos(precoUnitarioPreviewModal * qtdModal)})
            </button>
          </div>
        </div>
      )}
    </div>
  );
};
