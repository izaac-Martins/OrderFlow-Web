import React, { useCallback, useEffect, useState } from 'react';
import {
  ClipboardList,
  CheckCircle2,
  XCircle,
  ChefHat,
  Truck,
  Banknote,
  RefreshCw,
  AlertTriangle,
} from 'lucide-react';
import { apiRequest } from '../lib/http';
import { parseApiError } from '../lib/errors';
import { formatCentavos, formatDateTimeBr } from '../lib/formatters';
import { stompManager } from '../lib/stompClient';
import { useAuth } from '../context/AuthContext';
import { useManagerPin } from '../context/ManagerPinContext';
import type {
  PedidoResponse,
  SessaoCaixaGerencial,
  StatusPedido,
} from '../types/api';

const STATUS_BADGE: Record<StatusPedido, string> = {
  CRIADO: 'bg-slate-100 text-slate-800',
  AGUARDANDO_PAGAMENTO: 'bg-amber-100 text-amber-900',
  CONFIRMADO: 'bg-sky-100 text-sky-900',
  EM_PREPARO: 'bg-indigo-100 text-indigo-900',
  PRONTO: 'bg-emerald-500 text-white',
  ENTREGUE: 'bg-slate-200 text-slate-700',
  CANCELADO: 'bg-rose-100 text-rose-800',
  ESTORNADO: 'bg-rose-200 text-rose-900',
};

export const PedidosMonitorPage: React.FC = () => {
  const { user } = useAuth();
  const { requestManagerPin } = useManagerPin();

  const [pedidos, setPedidos] = useState<PedidoResponse[]>([]);
  const [sessaoAberta, setSessaoAberta] =
    useState<SessaoCaixaGerencial | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  const carregarPedidos = useCallback(async () => {
    try {
      const [lista, sessoes] = await Promise.all([
        apiRequest<PedidoResponse[]>({ method: 'GET', url: '/pedidos' }),
        apiRequest<SessaoCaixaGerencial[]>({
          method: 'GET',
          url: '/caixa/sessoes',
        }),
      ]);
      setPedidos(lista);
      setSessaoAberta(sessoes.find((s) => s.status === 'ABERTA') || null);
    } catch (err) {
      setErrorMsg(parseApiError(err).message);
    }
  }, []);

  useEffect(() => {
    carregarPedidos();
    const pollId = window.setInterval(() => {
      carregarPedidos();
    }, 1500);
    return () => window.clearInterval(pollId);
  }, [carregarPedidos]);

  // Atualização em tempo real via STOMP
  useEffect(() => {
    const lojaId = user?.lojaId || '019325f0-8a00-7000-8000-000000000001';
    const unsubKds = stompManager.subscribe(`/topic/lojas/${lojaId}/kds`, () =>
      carregarPedidos()
    );
    const unsubProntos = stompManager.subscribe(
      `/topic/lojas/${lojaId}/pedidos/prontos`,
      () => carregarPedidos()
    );
    return () => {
      unsubKds();
      unsubProntos();
    };
  }, [user?.lojaId, carregarPedidos]);

  const handleAvancarStatus = async (
    pedido: PedidoResponse,
    acao: 'confirmar' | 'iniciar-preparo' | 'marcar-pronto' | 'entregar'
  ) => {
    setErrorMsg(null);
    setSuccessMsg(null);
    const url =
      acao === 'iniciar-preparo' || acao === 'marcar-pronto'
        ? `/cozinha/pedidos/${pedido.id}/${acao}`
        : `/pedidos/${pedido.id}/${acao}`;

    try {
      await apiRequest({
        method: 'POST',
        url,
        data: { versao: pedido.versao },
      });
      setSuccessMsg(`Pedido #${pedido.numeroDiario} atualizado com sucesso.`);
      await carregarPedidos();
    } catch (err) {
      const apiErr = parseApiError(err);
      setErrorMsg(apiErr.message);
      await carregarPedidos();
    }
  };

  const handleQuitarPosPago = async (pedido: PedidoResponse) => {
    setErrorMsg(null);
    setSuccessMsg(null);
    if (!sessaoAberta) {
      setErrorMsg(
        'Abra uma sessão de caixa antes de quitar o saldo devedor do pedido.'
      );
      return;
    }

    try {
      await apiRequest({
        method: 'POST',
        url: '/pagamentos',
        data: {
          pedidoId: pedido.id,
          sessaoCaixaId: sessaoAberta.id,
          metodo: 'PIX',
          valorCentavos: pedido.saldoDevedorCentavos,
        },
      });
      setSuccessMsg(
        `Pedido #${pedido.numeroDiario} quitado integralmente (${formatCentavos(
          pedido.saldoDevedorCentavos
        )}).`
      );
      await carregarPedidos();
    } catch (err) {
      setErrorMsg(parseApiError(err).message);
    }
  };

  const executarCancelamento = async (
    pedido: PedidoResponse,
    motivo: string,
    pinAprovador?: string
  ) => {
    await apiRequest({
      method: 'POST',
      url: `/pedidos/${pedido.id}/cancelar`,
      data: {
        versao: pedido.versao,
        motivo,
        registrarPerda: pedido.status === 'EM_PREPARO' || pedido.status === 'PRONTO',
        sessaoCaixaId: sessaoAberta?.id,
        pinAprovador: pinAprovador || null,
      },
    });
    setSuccessMsg(`Pedido #${pedido.numeroDiario} cancelado/estornado.`);
    await carregarPedidos();
  };

  const handleCancelarPedido = async (pedido: PedidoResponse) => {
    setErrorMsg(null);
    setSuccessMsg(null);
    const motivo = 'Solicitação de cancelamento no monitor operacional';

    try {
      await executarCancelamento(pedido, motivo);
    } catch (err) {
      const apiErr = parseApiError(err);
      if (apiErr.code === 'CANCELAMENTO_REQUER_GERENTE') {
        const pinResult = await requestManagerPin({
          title: `Cancelar Pedido #${pedido.numeroDiario} (${pedido.status})`,
          description: apiErr.message,
          operationContext: `Pedido #${pedido.numeroDiario} — Valor: ${formatCentavos(
            pedido.valorTotalCentavos
          )}`,
        });
        if (pinResult) {
          try {
            await executarCancelamento(pedido, motivo, pinResult.pin);
          } catch (retryErr) {
            setErrorMsg(parseApiError(retryErr).message);
          }
        }
        return;
      }
      setErrorMsg(apiErr.message);
      await carregarPedidos();
    }
  };

  return (
    <div className="space-y-6">
      <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2 text-amber-700 text-xs font-bold uppercase tracking-wider">
            <ClipboardList className="w-4 h-4" />
            <span>Monitor em Tempo Real (STOMP + Concorrência `@Version`)</span>
          </div>
          <h1 className="text-2xl font-extrabold text-slate-900 mt-1">
            Acompanhamento de Pedidos, Entrega &amp; Cancelamento com Alçada
          </h1>
        </div>
        <button
          type="button"
          onClick={carregarPedidos}
          className="px-4 py-2.5 rounded-xl border border-slate-200 hover:bg-slate-50 text-xs font-bold text-slate-700 flex items-center gap-2 self-start sm:self-auto"
        >
          <RefreshCw className="w-4 h-4" />
          <span>Sincronizar</span>
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

      {pedidos.length === 0 ? (
        <div className="bg-white rounded-2xl border border-slate-200 p-10 text-center text-sm text-slate-500">
          Nenhum pedido registrado ainda. Emita um pedido na tela{' '}
          <strong>Balcão / PDV</strong> para acompanhar a máquina de estados.
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
          {pedidos.map((p) => (
            <div
              key={p.id}
              className="bg-white rounded-2xl border border-slate-200 p-5 shadow-sm flex flex-col justify-between gap-4"
            >
              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <span className="text-lg font-extrabold text-slate-900">
                      #{p.numeroDiario}
                    </span>
                    <span className="text-xs font-semibold text-slate-600">
                      {p.identificadorCliente}
                      {p.mesa ? ` · Mesa ${p.mesa}` : ''}
                    </span>
                  </div>
                  <span
                    className={`px-2.5 py-1 rounded-full text-[11px] font-bold ${
                      STATUS_BADGE[p.status]
                    }`}
                  >
                    {p.status} · v{p.versao}
                  </span>
                </div>

                <div className="space-y-1 bg-slate-50 p-3 rounded-xl border border-slate-200 text-xs">
                  {p.itens.map((it) => (
                    <div key={it.id} className="space-y-0.5">
                      <div className="flex justify-between font-semibold text-slate-800">
                        <span>
                          {it.quantidade}x {it.produtoNome}
                        </span>
                        <span>{formatCentavos(it.totalCentavos)}</span>
                      </div>
                      {it.modificadores.map((m) => (
                        <div
                          key={m.modificadorId}
                          className="text-[11px] text-slate-500 pl-3"
                        >
                          + {m.quantidade}x {m.modificadorNome}
                        </div>
                      ))}
                      {it.filhos.map((f) => (
                        <div
                          key={f.id}
                          className="text-[11px] text-purple-800 pl-3"
                        >
                          ↳ [{f.comboSlotNome}] {f.produtoNome}
                        </div>
                      ))}
                    </div>
                  ))}
                </div>

                <div className="flex items-center justify-between text-xs pt-1">
                  <span className="text-slate-500">
                    {formatDateTimeBr(p.createdAt)} · {p.modalidadePagamento}
                  </span>
                  <div className="text-right">
                    <strong className="text-sm text-slate-900 block">
                      Total: {formatCentavos(p.valorTotalCentavos)}
                    </strong>
                    {p.saldoDevedorCentavos > 0 && (
                      <span className="text-[11px] font-bold text-amber-700">
                        Em aberto: {formatCentavos(p.saldoDevedorCentavos)}
                      </span>
                    )}
                  </div>
                </div>
              </div>

              {/* Ações da Máquina de Estados */}
              <div className="pt-3 border-t border-slate-200 flex flex-wrap items-center gap-2">
                {p.saldoDevedorCentavos > 0 &&
                  p.status !== 'CANCELADO' &&
                  p.status !== 'ESTORNADO' && (
                    <button
                      type="button"
                      onClick={() => handleQuitarPosPago(p)}
                      className="px-3 py-1.5 rounded-xl bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-bold flex items-center gap-1"
                    >
                      <Banknote className="w-3.5 h-3.5" />
                      <span>Quitar Saldo</span>
                    </button>
                  )}

                {p.status === 'CONFIRMADO' && (
                  <button
                    type="button"
                    onClick={() => handleAvancarStatus(p, 'iniciar-preparo')}
                    className="px-3 py-1.5 rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white text-xs font-bold flex items-center gap-1"
                  >
                    <ChefHat className="w-3.5 h-3.5" />
                    <span>Iniciar Preparo (KDS)</span>
                  </button>
                )}

                {p.status === 'EM_PREPARO' && (
                  <button
                    type="button"
                    onClick={() => handleAvancarStatus(p, 'marcar-pronto')}
                    className="px-3 py-1.5 rounded-xl bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-bold flex items-center gap-1"
                  >
                    <CheckCircle2 className="w-3.5 h-3.5" />
                    <span>Marcar PRONTO</span>
                  </button>
                )}

                {p.status === 'PRONTO' && (
                  <button
                    type="button"
                    onClick={() => handleAvancarStatus(p, 'entregar')}
                    className="px-3 py-1.5 rounded-xl bg-amber-600 hover:bg-amber-700 text-white text-xs font-bold flex items-center gap-1"
                  >
                    <Truck className="w-3.5 h-3.5" />
                    <span>Entregar ao Cliente</span>
                  </button>
                )}

                {p.status !== 'ENTREGUE' &&
                  p.status !== 'CANCELADO' &&
                  p.status !== 'ESTORNADO' && (
                    <button
                      type="button"
                      onClick={() => handleCancelarPedido(p)}
                      className="ml-auto px-3 py-1.5 rounded-xl border border-rose-200 hover:bg-rose-50 text-rose-700 text-xs font-bold flex items-center gap-1"
                    >
                      <XCircle className="w-3.5 h-3.5" />
                      <span>
                        {p.status === 'EM_PREPARO' || p.status === 'PRONTO'
                          ? 'Cancelar (PIN)'
                          : 'Cancelar'}
                      </span>
                    </button>
                  )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};
