import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  ClipboardList,
  CheckCircle2,
  XCircle,
  ChefHat,
  Truck,
  RefreshCw,
  AlertTriangle,
  Clock,
  Utensils,
  User,
} from 'lucide-react';
import { apiRequest } from '../lib/http';
import { parseApiError } from '../lib/errors';
import { formatBigDecimalReais, formatDateTimeBr } from '../lib/formatters';
import type {
  PedidoResponseApiDTO,
  StatusPedidoCanonico,
} from '../types/api';

const STATUS_CONFIG: Record<
  StatusPedidoCanonico,
  { label: string; badgeClass: string; borderClass: string }
> = {
  RECEBIDO: {
    label: 'RECEBIDO (Novo)',
    badgeClass: 'bg-sky-100 text-sky-900 border border-sky-300',
    borderClass: 'border-sky-300',
  },
  EM_PREPARO: {
    label: 'EM PREPARO (Cozinha)',
    badgeClass: 'bg-amber-100 text-amber-900 border border-amber-300',
    borderClass: 'border-amber-300',
  },
  PRONTO: {
    label: 'PRONTO P/ ENTREGA',
    badgeClass: 'bg-emerald-600 text-white shadow-sm',
    borderClass: 'border-emerald-500 ring-1 ring-emerald-500/30',
  },
  ENTREGUE: {
    label: 'ENTREGUE NA MESA',
    badgeClass: 'bg-slate-200 text-slate-700',
    borderClass: 'border-slate-200 opacity-85',
  },
  CANCELADO: {
    label: 'CANCELADO',
    badgeClass: 'bg-rose-100 text-rose-800 border border-rose-200',
    borderClass: 'border-rose-200 opacity-75',
  },
};

export const PedidosMonitorPage: React.FC = () => {
  const [pedidos, setPedidos] = useState<PedidoResponseApiDTO[]>([]);
  const [filtroStatus, setFiltroStatus] = useState<'TODOS' | StatusPedidoCanonico>('TODOS');
  const [loadingId, setLoadingId] = useState<number | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  const carregarPedidos = useCallback(async () => {
    try {
      const lista = await apiRequest<PedidoResponseApiDTO[]>({
        method: 'GET',
        url: '/pedidos',
      });
      if (Array.isArray(lista)) {
        setPedidos(lista);
      }
    } catch (err) {
      setErrorMsg(parseApiError(err).message);
    }
  }, []);

  useEffect(() => {
    carregarPedidos();
    const pollId = window.setInterval(() => {
      carregarPedidos();
    }, 2500);
    return () => window.clearInterval(pollId);
  }, [carregarPedidos]);

  const handleAlterarStatus = async (
    pedido: PedidoResponseApiDTO,
    novoStatus: StatusPedidoCanonico
  ) => {
    setErrorMsg(null);
    setSuccessMsg(null);
    setLoadingId(pedido.id);

    try {
      const atualizado = await apiRequest<PedidoResponseApiDTO>({
        method: 'PATCH',
        url: `/pedidos/${pedido.id}/status`,
        data: { status: novoStatus },
      });
      setSuccessMsg(
        `Pedido #${atualizado.id} (Mesa ${String(atualizado.numeroMesa).padStart(
          2,
          '0'
        )}) avançou de ${pedido.status} para ${atualizado.status}.`
      );
      await carregarPedidos();
    } catch (err) {
      setErrorMsg(parseApiError(err).message);
      await carregarPedidos();
    } finally {
      setLoadingId(null);
    }
  };

  const contadores = useMemo(() => {
    return {
      TODOS: pedidos.length,
      RECEBIDO: pedidos.filter((p) => p.status === 'RECEBIDO').length,
      EM_PREPARO: pedidos.filter((p) => p.status === 'EM_PREPARO').length,
      PRONTO: pedidos.filter((p) => p.status === 'PRONTO').length,
      ENTREGUE: pedidos.filter((p) => p.status === 'ENTREGUE').length,
      CANCELADO: pedidos.filter((p) => p.status === 'CANCELADO').length,
    };
  }, [pedidos]);

  const pedidosVisiveis = useMemo(() => {
    if (filtroStatus === 'TODOS') return pedidos;
    return pedidos.filter((p) => p.status === filtroStatus);
  }, [pedidos, filtroStatus]);

  return (
    <div className="space-y-6">
      <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2 text-amber-700 text-xs font-bold uppercase tracking-wider">
            <ClipboardList className="w-4 h-4" />
            <span>Monitor Operacional em Tempo Real (Máquina de 5 Estados + `@Version`)</span>
          </div>
          <h1 className="text-2xl font-extrabold text-slate-900 mt-1">
            Comandas de Mesas, Fila de Produção &amp; Despacho (`PATCH /pedidos/&#123;id&#125;/status`)
          </h1>
        </div>
        <button
          type="button"
          onClick={carregarPedidos}
          className="px-4 py-2.5 rounded-xl border border-slate-200 hover:bg-slate-50 text-xs font-bold text-slate-700 flex items-center gap-2 self-start sm:self-auto"
        >
          <RefreshCw className="w-4 h-4" />
          <span>Sincronizar Agora</span>
        </button>
      </div>

      {/* Abas de Filtro pelos 5 Estados Canônicos */}
      <div className="flex flex-wrap items-center gap-2">
        {(
          [
            { key: 'TODOS', label: 'Todos os Pedidos' },
            { key: 'RECEBIDO', label: '1. Recebidos' },
            { key: 'EM_PREPARO', label: '2. Em Preparo' },
            { key: 'PRONTO', label: '3. Prontos' },
            { key: 'ENTREGUE', label: '4. Entregues' },
            { key: 'CANCELADO', label: '5. Cancelados' },
          ] as Array<{ key: 'TODOS' | StatusPedidoCanonico; label: string }>
        ).map((tab) => (
          <button
            key={tab.key}
            type="button"
            onClick={() => setFiltroStatus(tab.key)}
            className={`px-3.5 py-2 rounded-xl text-xs font-bold flex items-center gap-2 transition ${
              filtroStatus === tab.key
                ? 'bg-slate-900 text-white shadow-sm'
                : 'bg-white border border-slate-200 text-slate-700 hover:bg-slate-50'
            }`}
          >
            <span>{tab.label}</span>
            <span
              className={`px-1.5 py-0.5 rounded-md text-[10px] font-mono ${
                filtroStatus === tab.key
                  ? 'bg-amber-500 text-slate-950 font-black'
                  : 'bg-slate-100 text-slate-600'
              }`}
            >
              {contadores[tab.key]}
            </span>
          </button>
        ))}
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

      {pedidosVisiveis.length === 0 ? (
        <div className="bg-white rounded-2xl border border-slate-200 p-10 text-center text-sm text-slate-500">
          Nenhum pedido registrado neste status. Os pedidos enviados pelo App Mobile ou pelo Balcão aparecerão automaticamente aqui.
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
          {pedidosVisiveis.map((p) => {
            const cfg = STATUS_CONFIG[p.status] || STATUS_CONFIG.RECEBIDO;
            const isBusy = loadingId === p.id;

            return (
              <div
                key={p.id}
                className={`bg-white rounded-2xl border-2 p-5 shadow-sm flex flex-col justify-between gap-4 transition ${cfg.borderClass}`}
              >
                <div className="space-y-3">
                  {/* Topo do Card: Número do Pedido, Mesa e Status */}
                  <div className="flex items-center justify-between gap-2">
                    <div className="flex items-center gap-2">
                      <span className="text-lg font-black text-slate-900 font-mono">
                        #{p.id}
                      </span>
                      <span className="px-2.5 py-0.5 rounded-lg bg-amber-100 text-amber-950 text-xs font-extrabold flex items-center gap-1">
                        <Utensils className="w-3 h-3" />
                        Mesa {String(p.numeroMesa).padStart(2, '0')}
                      </span>
                    </div>

                    <span
                      className={`px-2.5 py-1 rounded-full text-[11px] font-bold ${cfg.badgeClass}`}
                    >
                      {cfg.label}
                      {p.versao !== undefined ? ` · v${p.versao}` : ''}
                    </span>
                  </div>

                  {/* Itens da Comanda */}
                  <div className="space-y-2 bg-slate-50 p-3.5 rounded-xl border border-slate-200 text-xs">
                    {(p.itens || []).map((it, idx) => (
                      <div
                        key={it.id ?? idx}
                        className="pb-2 last:pb-0 border-b last:border-b-0 border-slate-200/70 space-y-1"
                      >
                        <div className="flex justify-between font-bold text-slate-900">
                          <span>
                            {it.quantidade}x {it.nomeProduto}
                            {it.tamanhoEscolhido &&
                            it.tamanhoEscolhido !== 'Padrão'
                              ? ` (${it.tamanhoEscolhido})`
                              : ''}
                          </span>
                          <span className="text-amber-800">
                            {formatBigDecimalReais(it.precoTotalItem)}
                          </span>
                        </div>

                        <div className="flex flex-wrap gap-1.5 text-[11px] text-slate-600">
                          {it.tipoGraoEscolhido && (
                            <span className="px-1.5 py-0.5 rounded bg-white border border-slate-200">
                              Tipo: {it.tipoGraoEscolhido}
                            </span>
                          )}
                          {it.adicionaisEscolhidos &&
                            it.adicionaisEscolhidos !== 'Nenhum' && (
                              <span className="px-1.5 py-0.5 rounded bg-amber-50 border border-amber-200 text-amber-900 font-medium">
                                + {it.adicionaisEscolhidos}
                              </span>
                            )}
                          {it.pagadorItem && (
                            <span className="px-1.5 py-0.5 rounded bg-indigo-50 border border-indigo-200 text-indigo-900 font-semibold flex items-center gap-1">
                              <User className="w-3 h-3" />
                              {it.pagadorItem}
                            </span>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>

                  {/* Rodapé de Metadados Financeiros */}
                  <div className="flex items-center justify-between text-xs pt-1">
                    <span className="text-slate-500 flex items-center gap-1">
                      <Clock className="w-3.5 h-3.5" />
                      {formatDateTimeBr(p.criadoEm)}
                    </span>
                    <strong className="text-base font-extrabold text-slate-900">
                      Total: {formatBigDecimalReais(p.valorTotal)}
                    </strong>
                  </div>
                </div>

                {/* Botões de Ação da Máquina de Estados (RECEBIDO -> EM_PREPARO -> PRONTO -> ENTREGUE / CANCELADO) */}
                <div className="pt-3 border-t border-slate-200 flex flex-wrap items-center gap-2">
                  {p.status === 'RECEBIDO' && (
                    <button
                      type="button"
                      disabled={isBusy}
                      onClick={() => handleAlterarStatus(p, 'EM_PREPARO')}
                      className="flex-1 py-2 px-3 rounded-xl bg-amber-600 hover:bg-amber-700 disabled:opacity-50 text-white text-xs font-extrabold flex items-center justify-center gap-1.5 shadow-sm transition"
                    >
                      <ChefHat className="w-4 h-4" />
                      <span>Iniciar Preparo</span>
                    </button>
                  )}

                  {p.status === 'EM_PREPARO' && (
                    <button
                      type="button"
                      disabled={isBusy}
                      onClick={() => handleAlterarStatus(p, 'PRONTO')}
                      className="flex-1 py-2 px-3 rounded-xl bg-emerald-600 hover:bg-emerald-700 disabled:opacity-50 text-white text-xs font-extrabold flex items-center justify-center gap-1.5 shadow-sm transition"
                    >
                      <CheckCircle2 className="w-4 h-4" />
                      <span>Marcar PRONTO</span>
                    </button>
                  )}

                  {p.status === 'PRONTO' && (
                    <button
                      type="button"
                      disabled={isBusy}
                      onClick={() => handleAlterarStatus(p, 'ENTREGUE')}
                      className="flex-1 py-2 px-3 rounded-xl bg-sky-600 hover:bg-sky-700 disabled:opacity-50 text-white text-xs font-extrabold flex items-center justify-center gap-1.5 shadow-sm transition"
                    >
                      <Truck className="w-4 h-4" />
                      <span>Entregar na Mesa</span>
                    </button>
                  )}

                  {p.status !== 'ENTREGUE' && p.status !== 'CANCELADO' && (
                    <button
                      type="button"
                      disabled={isBusy}
                      onClick={() => handleAlterarStatus(p, 'CANCELADO')}
                      className="px-3 py-2 rounded-xl border border-rose-200 hover:bg-rose-50 disabled:opacity-50 text-rose-700 text-xs font-bold flex items-center gap-1 transition"
                    >
                      <XCircle className="w-3.5 h-3.5" />
                      <span>Cancelar</span>
                    </button>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};
