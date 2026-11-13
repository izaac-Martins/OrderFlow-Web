import React, { useState } from 'react';
import {
  Clock,
  Flame,
  Coffee,
  Utensils,
  CheckCircle2,
  RotateCcw,
  Send,
  AlertTriangle,
  MessageSquareWarning,
  Layers,
  User,
  MapPin,
} from 'lucide-react';
import type { PedidoCozinhaDto } from '../types/kds';

interface KdsOrderCardProps {
  pedido: PedidoCozinhaDto;
  nowMs: number;
  loadingPedidoId: string | null;
  onIniciarPreparo: (pedido: PedidoCozinhaDto) => void;
  onMarcarPronto: (pedido: PedidoCozinhaDto) => void;
  onRefazer: (pedido: PedidoCozinhaDto, motivo: string) => void;
  onEntregar: (pedido: PedidoCozinhaDto) => void;
}

export const KdsOrderCard: React.FC<KdsOrderCardProps> = ({
  pedido,
  nowMs,
  loadingPedidoId,
  onIniciarPreparo,
  onMarcarPronto,
  onRefazer,
  onEntregar,
}) => {
  const [showRefazerModal, setShowRefazerModal] = useState(false);
  const [motivoRefazer, setMotivoRefazer] = useState(
    'Ajuste solicitado no padrão de bebida/preparo'
  );

  const confirmadoMs = new Date(pedido.confirmadoEm).getTime();
  const elapsedSec = Math.max(0, Math.floor((nowMs - confirmadoMs) / 1000));
  const elapsedMin = Math.floor(elapsedSec / 60);
  const remSec = elapsedSec % 60;
  const formattedTimer = `${String(elapsedMin).padStart(2, '0')}:${String(
    remSec
  ).padStart(2, '0')}`;

  // Regra de SLA Visual Dinâmico (< 5 min OK, 5-10 min Atenção, > 10 min Crítico)
  const isReady = pedido.status === 'PRONTO';
  const slaLevel: 'OK' | 'WARNING' | 'CRITICAL' = isReady
    ? 'OK'
    : elapsedMin >= 10
    ? 'CRITICAL'
    : elapsedMin >= 5
    ? 'WARNING'
    : 'OK';

  const borderClasses = isReady
    ? 'border-emerald-500/70 bg-emerald-950/20 shadow-emerald-950/30'
    : slaLevel === 'CRITICAL'
    ? 'border-rose-500 bg-rose-950/25 shadow-rose-950/40 ring-1 ring-rose-500/50'
    : slaLevel === 'WARNING'
    ? 'border-amber-500/80 bg-amber-950/15 shadow-amber-950/30'
    : 'border-slate-800 bg-slate-900/90';

  const timerBadgeClasses = isReady
    ? 'bg-emerald-500/20 text-emerald-300 border-emerald-500/40'
    : slaLevel === 'CRITICAL'
    ? 'bg-rose-600 text-white border-rose-400 animate-pulse'
    : slaLevel === 'WARNING'
    ? 'bg-amber-500/25 text-amber-300 border-amber-500/50'
    : 'bg-emerald-500/15 text-emerald-300 border-emerald-500/30';

  const isBusy = loadingPedidoId === pedido.pedidoId;

  return (
    <article
      className={`flex flex-col justify-between rounded-2xl border-2 transition-all shadow-lg overflow-hidden ${borderClasses}`}
    >
      {/* Cabeçalho do Ticket */}
      <div>
        <header className="px-4 py-3.5 border-b border-slate-800/80 bg-slate-950/60 flex items-center justify-between gap-2">
          <div className="flex items-center gap-2.5">
            <span className="text-2xl font-black tracking-tight text-white font-mono">
              #{pedido.numeroDiario}
            </span>
            <div className="flex flex-col">
              <div className="flex items-center gap-1.5">
                {pedido.tipoAtendimento === 'MESA' && pedido.mesa ? (
                  <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md text-xs font-extrabold uppercase bg-indigo-500/20 text-indigo-300 border border-indigo-500/40">
                    <MapPin className="w-3 h-3" />
                    Mesa {pedido.mesa}
                  </span>
                ) : (
                  <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md text-xs font-bold uppercase bg-slate-800 text-slate-300 border border-slate-700">
                    {pedido.tipoAtendimento}
                  </span>
                )}
                <span className="text-[11px] font-mono text-slate-500">
                  v{pedido.versao}
                </span>
              </div>
              {pedido.identificadorCliente && (
                <span className="text-xs font-semibold text-slate-300 flex items-center gap-1 mt-0.5">
                  <User className="w-3 h-3 text-slate-500" />
                  {pedido.identificadorCliente}
                </span>
              )}
            </div>
          </div>

          {/* Cronômetro e SLA */}
          <div className="flex flex-col items-end gap-1">
            <div
              className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-xl border text-sm font-mono font-bold ${timerBadgeClasses}`}
              title="Tempo decorrido desde a confirmação do pedido"
            >
              <Clock className="w-4 h-4" />
              <span>{formattedTimer}</span>
            </div>
            <span
              className={`text-[10px] font-extrabold uppercase tracking-wider px-2 py-0.5 rounded ${
                pedido.status === 'CONFIRMADO'
                  ? 'bg-sky-500/20 text-sky-300'
                  : pedido.status === 'EM_PREPARO'
                  ? 'bg-amber-500/20 text-amber-300'
                  : 'bg-emerald-500/20 text-emerald-300'
              }`}
            >
              {pedido.status === 'CONFIRMADO'
                ? 'Fila / Novo'
                : pedido.status === 'EM_PREPARO'
                ? 'Em Preparo'
                : 'Pronto p/ Balcão'}
            </span>
          </div>
        </header>

        {/* Alerta de Observação Geral do Pedido */}
        {pedido.observacao && (
          <div className="mx-3.5 mt-3 px-3 py-2 rounded-xl bg-amber-500/15 border border-amber-500/40 flex items-start gap-2 text-amber-200 text-xs font-semibold">
            <MessageSquareWarning className="w-4 h-4 text-amber-400 shrink-0 mt-0.5" />
            <span>{pedido.observacao}</span>
          </div>
        )}

        {/* Lista de Itens de Produção (Sem preços, foco 100% operacional) */}
        <ul className="p-3.5 space-y-2.5">
          {pedido.itens.map((item) => (
            <li
              key={item.itemId}
              className="p-3 rounded-xl bg-slate-950/75 border border-slate-800/90 space-y-1.5"
            >
              {/* Badge de Combo Pai (se aplicável) */}
              {item.comboNome && (
                <div className="inline-flex items-center gap-1 px-2 py-0.5 rounded bg-purple-500/15 border border-purple-500/30 text-purple-300 text-[11px] font-bold">
                  <Layers className="w-3 h-3" />
                  <span>{item.comboNome}</span>
                </div>
              )}

              <div className="flex items-start justify-between gap-2">
                <div className="flex items-baseline gap-2">
                  <span className="inline-flex items-center justify-center min-w-[2rem] h-7 px-2 rounded-lg bg-amber-500/20 border border-amber-500/40 text-amber-300 font-black text-base font-mono">
                    {item.quantidade}x
                  </span>
                  <span className="text-base font-bold text-white leading-snug">
                    {item.produtoNome}
                  </span>
                </div>

                {/* Badge de Estação */}
                <span
                  className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-md text-[11px] font-bold uppercase shrink-0 ${
                    item.estacao === 'BAR'
                      ? 'bg-cyan-500/15 text-cyan-300 border border-cyan-500/30'
                      : 'bg-orange-500/15 text-orange-300 border border-orange-500/30'
                  }`}
                >
                  {item.estacao === 'BAR' ? (
                    <Coffee className="w-3 h-3" />
                  ) : (
                    <Utensils className="w-3 h-3" />
                  )}
                  {item.estacao}
                </span>
              </div>

              {/* Modificadores em Destaque */}
              {item.modificadores && item.modificadores.length > 0 && (
                <div className="flex flex-wrap gap-1.5 pt-1 pl-9">
                  {item.modificadores.map((mod, idx) => (
                    <span
                      key={`${item.itemId}-mod-${idx}`}
                      className="px-2.5 py-0.5 rounded-md bg-indigo-500/20 border border-indigo-500/40 text-indigo-200 text-xs font-semibold"
                    >
                      + {mod}
                    </span>
                  ))}
                </div>
              )}

              {/* Observação específica do item */}
              {item.observacao && (
                <div className="pl-9 pt-0.5">
                  <p className="text-xs font-bold text-rose-300 bg-rose-500/15 border border-rose-500/30 rounded-lg px-2.5 py-1 inline-flex items-center gap-1.5">
                    <AlertTriangle className="w-3.5 h-3.5 text-rose-400 shrink-0" />
                    <span>Obs: {item.observacao}</span>
                  </p>
                </div>
              )}
            </li>
          ))}
        </ul>
      </div>

      {/* Rodapé com Botões Touch de Alta Densidade */}
      <footer className="p-3.5 pt-2 border-t border-slate-800/80 bg-slate-950/50">
        {pedido.status === 'CONFIRMADO' && (
          <button
            type="button"
            disabled={isBusy}
            onClick={() => onIniciarPreparo(pedido)}
            className="w-full py-3.5 px-4 rounded-xl bg-amber-500 hover:bg-amber-400 active:scale-[0.99] disabled:opacity-50 text-slate-950 font-black text-base tracking-wide flex items-center justify-center gap-2 shadow-lg shadow-amber-500/20 transition cursor-pointer"
          >
            <Flame className="w-5 h-5 fill-current" />
            <span>INICIAR PREPARO</span>
          </button>
        )}

        {pedido.status === 'EM_PREPARO' && (
          <button
            type="button"
            disabled={isBusy}
            onClick={() => onMarcarPronto(pedido)}
            className="w-full py-3.5 px-4 rounded-xl bg-emerald-500 hover:bg-emerald-400 active:scale-[0.99] disabled:opacity-50 text-slate-950 font-black text-base tracking-wide flex items-center justify-center gap-2 shadow-lg shadow-emerald-500/20 transition cursor-pointer"
          >
            <CheckCircle2 className="w-5 h-5" />
            <span>MARCAR PRONTO (CHAMAR BALCÃO)</span>
          </button>
        )}

        {pedido.status === 'PRONTO' && !showRefazerModal && (
          <div className="grid grid-cols-2 gap-2.5">
            <button
              type="button"
              disabled={isBusy}
              onClick={() => setShowRefazerModal(true)}
              className="py-3 px-3 rounded-xl bg-slate-800 hover:bg-rose-950/80 hover:border-rose-500/50 border border-slate-700 text-slate-200 hover:text-rose-200 font-bold text-sm flex items-center justify-center gap-1.5 transition cursor-pointer"
            >
              <RotateCcw className="w-4 h-4" />
              <span>Refazer</span>
            </button>
            <button
              type="button"
              disabled={isBusy}
              onClick={() => onEntregar(pedido)}
              className="py-3 px-3 rounded-xl bg-sky-500 hover:bg-sky-400 text-slate-950 font-black text-sm flex items-center justify-center gap-1.5 shadow-md shadow-sky-500/20 transition cursor-pointer"
            >
              <Send className="w-4 h-4" />
              <span>Entregar</span>
            </button>
          </div>
        )}

        {/* Mini-modal embutido para confirmar motivo de Refazer */}
        {showRefazerModal && (
          <div className="space-y-2.5 p-2.5 rounded-xl bg-slate-900 border border-rose-500/50">
            <label className="block text-xs font-bold text-rose-300">
              Motivo para retornar à fila de preparo:
            </label>
            <input
              type="text"
              value={motivoRefazer}
              onChange={(e) => setMotivoRefazer(e.target.value)}
              className="w-full px-3 py-2 rounded-lg bg-slate-950 border border-slate-700 text-xs text-white focus:outline-none focus:border-rose-400"
              placeholder="Ex: Bebida esfriou / Ajuste de leite"
            />
            <div className="flex gap-2">
              <button
                type="button"
                onClick={() => setShowRefazerModal(false)}
                className="flex-1 py-2 rounded-lg bg-slate-800 text-xs font-bold text-slate-300 hover:bg-slate-700"
              >
                Cancelar
              </button>
              <button
                type="button"
                disabled={isBusy || !motivoRefazer.trim()}
                onClick={() => {
                  onRefazer(pedido, motivoRefazer.trim());
                  setShowRefazerModal(false);
                }}
                className="flex-1 py-2 rounded-lg bg-rose-600 hover:bg-rose-500 text-xs font-black text-white"
              >
                Confirmar Refazer
              </button>
            </div>
          </div>
        )}
      </footer>
    </article>
  );
};
