import React, { createContext, useCallback, useContext, useState } from 'react';
import { ShieldAlert, KeyRound, Lock, X, CheckCircle2, Delete } from 'lucide-react';
import { apiRequest } from '../lib/http';
import { parseApiError } from '../lib/errors';
import type { ValidarPinResponse } from '../types/api';

export interface ManagerPinPromptOptions {
  title: string;
  description: string;
  operationContext?: string;
}

export interface ManagerPinResult {
  pin: string;
  aprovadorNome: string;
  aprovadorId: string;
}

interface ManagerPinContextValue {
  requestManagerPin: (
    options: ManagerPinPromptOptions
  ) => Promise<ManagerPinResult | null>;
}

const ManagerPinContext = createContext<ManagerPinContextValue | undefined>(
  undefined
);

export const ManagerPinProvider: React.FC<{ children: React.ReactNode }> = ({
  children,
}) => {
  const [activePrompt, setActivePrompt] = useState<{
    options: ManagerPinPromptOptions;
    resolve: (result: ManagerPinResult | null) => void;
  } | null>(null);

  const [pin, setPin] = useState('');
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const requestManagerPin = useCallback(
    (options: ManagerPinPromptOptions): Promise<ManagerPinResult | null> => {
      setPin('');
      setErrorMsg(null);
      setLoading(false);
      return new Promise<ManagerPinResult | null>((resolve) => {
        setActivePrompt({ options, resolve });
      });
    },
    []
  );

  const handleClose = () => {
    if (!activePrompt) return;
    activePrompt.resolve(null);
    setActivePrompt(null);
    setPin('');
    setErrorMsg(null);
  };

  const handleDigit = (digit: string) => {
    if (pin.length >= 6) return;
    setErrorMsg(null);
    setPin((prev) => prev + digit);
  };

  const handleBackspace = () => {
    setErrorMsg(null);
    setPin((prev) => prev.slice(0, -1));
  };

  const handleSubmit = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    if (!activePrompt) return;
    if (pin.length < 4) {
      setErrorMsg('O PIN de alçada deve conter entre 4 e 6 dígitos numéricos.');
      return;
    }

    setLoading(true);
    setErrorMsg(null);

    try {
      const validacao = await apiRequest<ValidarPinResponse>({
        method: 'POST',
        url: '/auth/validar-pin',
        data: {
          pin,
          operacao: activePrompt.options.operationContext || activePrompt.options.title,
        },
      });

      const result: ManagerPinResult = {
        pin,
        aprovadorId: validacao.aprovadorId,
        aprovadorNome: validacao.aprovadorNome,
      };
      activePrompt.resolve(result);
      setActivePrompt(null);
      setPin('');
    } catch (err) {
      const apiErr = parseApiError(err);
      setErrorMsg(apiErr.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <ManagerPinContext.Provider value={{ requestManagerPin }}>
      {children}

      {activePrompt && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/70 backdrop-blur-sm p-4"
          role="dialog"
          aria-modal="true"
          aria-labelledby="modal-pin-title"
        >
          <div className="w-full max-w-md rounded-2xl bg-white shadow-2xl border border-slate-200 overflow-hidden">
            {/* Cabeçalho de Alçada */}
            <div className="bg-amber-600 px-6 py-4 text-white flex items-center justify-between">
              <div className="flex items-center gap-3">
                <div className="p-2 rounded-xl bg-white/20">
                  <ShieldAlert className="w-6 h-6" />
                </div>
                <div>
                  <span className="text-xs uppercase tracking-wider font-semibold text-amber-100">
                    Alçada Gerencial Obrigatória
                  </span>
                  <h2 id="modal-pin-title" className="text-lg font-bold">
                    {activePrompt.options.title}
                  </h2>
                </div>
              </div>
              <button
                type="button"
                onClick={handleClose}
                className="p-1.5 rounded-lg hover:bg-white/20 transition"
                aria-label="Fechar modal"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleSubmit} className="p-6 space-y-5">
              <p className="text-sm text-slate-600 leading-relaxed">
                {activePrompt.options.description}
              </p>

              {activePrompt.options.operationContext && (
                <div className="rounded-xl bg-amber-50 border border-amber-200 px-4 py-2.5 text-xs font-medium text-amber-900 flex items-center gap-2">
                  <KeyRound className="w-4 h-4 text-amber-700 shrink-0" />
                  <span>{activePrompt.options.operationContext}</span>
                </div>
              )}

              {/* Visor de PIN */}
              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-500 mb-2">
                  PIN de Gerente / Admin (4 a 6 dígitos)
                </label>
                <div className="relative">
                  <Lock className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
                  <input
                    type="password"
                    inputMode="numeric"
                    maxLength={6}
                    autoFocus
                    value={pin}
                    onChange={(e) => {
                      setErrorMsg(null);
                      setPin(e.target.value.replace(/\D/g, '').slice(0, 6));
                    }}
                    placeholder="••••"
                    className="w-full pl-10 pr-4 py-3 text-center text-2xl tracking-[0.5em] font-mono font-bold rounded-xl border border-slate-300 focus:border-amber-600 focus:ring-2 focus:ring-amber-600/20 outline-none"
                  />
                </div>
                <p className="mt-1.5 text-[11px] text-slate-500">
                  Dica de homologação: utilize o PIN gerencial{' '}
                  <code className="px-1.5 py-0.5 rounded bg-slate-100 font-mono font-bold text-slate-800">
                    4321
                  </code>{' '}
                  ou{' '}
                  <code className="px-1.5 py-0.5 rounded bg-slate-100 font-mono font-bold text-slate-800">
                    1234
                  </code>
                  .
                </p>
              </div>

              {/* Teclado Numérico Touch */}
              <div className="grid grid-cols-3 gap-2">
                {['1', '2', '3', '4', '5', '6', '7', '8', '9'].map((d) => (
                  <button
                    key={d}
                    type="button"
                    onClick={() => handleDigit(d)}
                    className="py-2.5 rounded-xl bg-slate-100 hover:bg-slate-200 active:bg-slate-300 text-slate-800 font-bold text-lg transition"
                  >
                    {d}
                  </button>
                ))}
                <button
                  type="button"
                  onClick={() => setPin('')}
                  className="py-2.5 rounded-xl bg-slate-100 hover:bg-slate-200 text-xs font-semibold text-slate-600 transition"
                >
                  LIMPAR
                </button>
                <button
                  type="button"
                  onClick={() => handleDigit('0')}
                  className="py-2.5 rounded-xl bg-slate-100 hover:bg-slate-200 active:bg-slate-300 text-slate-800 font-bold text-lg transition"
                >
                  0
                </button>
                <button
                  type="button"
                  onClick={handleBackspace}
                  className="py-2.5 rounded-xl bg-slate-100 hover:bg-slate-200 flex items-center justify-center text-slate-700 transition"
                  aria-label="Apagar dígito"
                >
                  <Delete className="w-5 h-5" />
                </button>
              </div>

              {errorMsg && (
                <div className="rounded-xl bg-rose-50 border border-rose-200 p-3 text-xs font-medium text-rose-700">
                  {errorMsg}
                </div>
              )}

              <div className="flex items-center justify-end gap-3 pt-2">
                <button
                  type="button"
                  onClick={handleClose}
                  className="px-4 py-2.5 rounded-xl border border-slate-300 text-slate-700 text-sm font-semibold hover:bg-slate-50 transition"
                >
                  Cancelar
                </button>
                <button
                  type="submit"
                  disabled={loading || pin.length < 4}
                  className="px-5 py-2.5 rounded-xl bg-amber-600 hover:bg-amber-700 disabled:opacity-50 text-white text-sm font-semibold flex items-center gap-2 shadow-sm transition"
                >
                  <CheckCircle2 className="w-4 h-4" />
                  <span>{loading ? 'Validando PIN...' : 'Autorizar Operação'}</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </ManagerPinContext.Provider>
  );
};

export function useManagerPin(): ManagerPinContextValue {
  const ctx = useContext(ManagerPinContext);
  if (!ctx) {
    throw new Error(
      'useManagerPin deve ser utilizado dentro de um ManagerPinProvider'
    );
  }
  return ctx;
}
