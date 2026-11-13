import React, { useEffect, useState } from 'react';
import {
  AlertOctagon,
  CheckCircle2,
  X,
  RefreshCw,
  Radio,
  ShieldAlert,
} from 'lucide-react';
import type { Insumo } from '../types/kds';
import {
  alternarDisponibilidadeInsumoKds,
  listarInsumosKds,
} from '../lib/kdsApi';

interface DrawerRuptura86Props {
  isOpen: boolean;
  onClose: () => void;
  onInsumoUpdated: (mensagem: string) => void;
}

export const DrawerRuptura86: React.FC<DrawerRuptura86Props> = ({
  isOpen,
  onClose,
  onInsumoUpdated,
}) => {
  const [insumos, setInsumos] = useState<Insumo[]>([]);
  const [loading, setLoading] = useState(false);
  const [togglingId, setTogglingId] = useState<string | null>(null);
  const [motivoPadrao, setMotivoPadrao] = useState(
    'Fim de lote na bancada da cozinha (Ruptura 86)'
  );

  const carregarInsumos = async () => {
    setLoading(true);
    try {
      const lista = await listarInsumosKds();
      setInsumos(lista);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (isOpen) {
      carregarInsumos();
    }
  }, [isOpen]);

  if (!isOpen) return null;

  const handleToggle = async (insumo: Insumo) => {
    setTogglingId(insumo.id);
    try {
      const novoStatus = !insumo.disponivel;
      const resp = await alternarDisponibilidadeInsumoKds(
        insumo.id,
        novoStatus,
        motivoPadrao
      );
      setInsumos((prev) =>
        prev.map((item) => (item.id === insumo.id ? resp.insumo : item))
      );
      onInsumoUpdated(
        novoStatus
          ? `Insumo "${insumo.nome}" reativado no cardápio do PDV!`
          : `ALERTA 86: "${insumo.nome}" pausado! Produtos dependentes bloqueados no PDV em tempo real.`
      );
    } finally {
      setTogglingId(null);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex justify-end bg-black/75 backdrop-blur-sm">
      <div className="w-full max-w-lg bg-slate-900 border-l border-slate-800 h-full flex flex-col justify-between shadow-2xl">
        {/* Header */}
        <div className="p-5 border-b border-slate-800 flex items-center justify-between bg-slate-950">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-rose-500/20 border border-rose-500/40 flex items-center justify-center text-rose-400">
              <AlertOctagon className="w-6 h-6" />
            </div>
            <div>
              <h2 className="text-lg font-black text-white tracking-tight">
                Controle Rápido de Ruptura (86)
              </h2>
              <p className="text-xs text-slate-400 flex items-center gap-1.5">
                <Radio className="w-3.5 h-3.5 text-emerald-400 animate-pulse" />
                <span>
                  Sincroniza via STOMP com todos os terminais de PDV
                </span>
              </p>
            </div>
          </div>

          <button
            type="button"
            onClick={onClose}
            className="p-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-300 cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Body */}
        <div className="p-5 overflow-y-auto flex-1 space-y-4">
          <div className="p-3.5 rounded-xl bg-amber-500/10 border border-amber-500/30 text-xs text-amber-200 flex items-start gap-2.5">
            <ShieldAlert className="w-5 h-5 text-amber-400 shrink-0 mt-0.5" />
            <div>
              <p className="font-bold">Bloqueio Imediato de Venda</p>
              <p className="text-amber-300/90 mt-0.5">
                Ao pausar um insumo crítico na cozinha, todos os cafés, combos
                ou adicionais que dependem da sua ficha técnica recebem tarja
                de esgotado no PDV em menos de 1 segundo.
              </p>
            </div>
          </div>

          <div>
            <label className="block text-xs font-bold uppercase tracking-wider text-slate-400 mb-1.5">
              Motivo padrão da pausa (enviado ao PDV):
            </label>
            <input
              type="text"
              value={motivoPadrao}
              onChange={(e) => setMotivoPadrao(e.target.value)}
              className="w-full px-3.5 py-2.5 rounded-xl bg-slate-950 border border-slate-800 text-sm text-white focus:outline-none focus:border-rose-500"
            />
          </div>

          <div className="flex items-center justify-between pt-2">
            <span className="text-xs font-bold uppercase tracking-wider text-slate-400">
              Insumos da Bancada ({insumos.length})
            </span>
            <button
              type="button"
              onClick={carregarInsumos}
              className="inline-flex items-center gap-1.5 text-xs font-semibold text-sky-400 hover:text-sky-300 cursor-pointer"
            >
              <RefreshCw
                className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`}
              />
              Atualizar
            </button>
          </div>

          <div className="space-y-3">
            {insumos.map((insumo) => {
              const isBusy = togglingId === insumo.id;
              return (
                <div
                  key={insumo.id}
                  className={`p-4 rounded-2xl border transition flex items-center justify-between gap-4 ${
                    insumo.disponivel
                      ? 'bg-slate-950/70 border-slate-800'
                      : 'bg-rose-950/25 border-rose-500/50'
                  }`}
                >
                  <div className="space-y-1">
                    <div className="flex items-center gap-2">
                      <span className="font-bold text-white text-base">
                        {insumo.nome}
                      </span>
                      <span
                        className={`px-2 py-0.5 rounded text-[10px] font-black uppercase ${
                          insumo.disponivel
                            ? 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/30'
                            : 'bg-rose-500/20 text-rose-300 border border-rose-500/40'
                        }`}
                      >
                        {insumo.disponivel ? 'DISPONÍVEL' : 'PAUSADO (86)'}
                      </span>
                    </div>
                    <p className="text-xs text-slate-400">
                      Estoque estimado: {insumo.quantidadeAtual}{' '}
                      {insumo.unidadeMedida || insumo.unidade}
                    </p>
                    {!insumo.disponivel && insumo.motivoIndisponibilidade && (
                      <p className="text-xs text-rose-300 font-medium">
                        Motivo: {insumo.motivoIndisponibilidade}
                      </p>
                    )}
                  </div>

                  <button
                    type="button"
                    disabled={isBusy}
                    onClick={() => handleToggle(insumo)}
                    className={`px-4 py-3 rounded-xl font-black text-xs uppercase tracking-wider shrink-0 transition cursor-pointer flex items-center gap-1.5 ${
                      insumo.disponivel
                        ? 'bg-rose-600 hover:bg-rose-500 text-white shadow-lg shadow-rose-600/20'
                        : 'bg-emerald-500 hover:bg-emerald-400 text-slate-950 shadow-lg shadow-emerald-500/20'
                    }`}
                  >
                    {insumo.disponivel ? (
                      <>
                        <AlertOctagon className="w-4 h-4" />
                        <span>Pausar (86)</span>
                      </>
                    ) : (
                      <>
                        <CheckCircle2 className="w-4 h-4" />
                        <span>Reativar</span>
                      </>
                    )}
                  </button>
                </div>
              );
            })}
          </div>
        </div>

        {/* Footer */}
        <div className="p-4 border-t border-slate-800 bg-slate-950 flex justify-end">
          <button
            type="button"
            onClick={onClose}
            className="px-5 py-2.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-sm font-bold text-white cursor-pointer"
          >
            Fechar Painel 86
          </button>
        </div>
      </div>
    </div>
  );
};
