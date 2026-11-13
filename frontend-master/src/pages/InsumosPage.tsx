import React, { useCallback, useEffect, useState } from 'react';
import {
  PackageCheck,
  AlertTriangle,
  Plus,
  CheckCircle2,
  XCircle,
  Zap,
  RefreshCw,
} from 'lucide-react';
import { apiRequest } from '../lib/http';
import { parseApiError } from '../lib/errors';
import { stompManager } from '../lib/stompClient';
import { useAuth } from '../context/AuthContext';
import type {
  CatalogoEventoStomp,
  Insumo,
  InsumoDisponibilidadeResponse,
} from '../types/api';

export const InsumosPage: React.FC = () => {
  const { user } = useAuth();
  const [insumos, setInsumos] = useState<Insumo[]>([]);
  const [loading, setLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [ultimoImpacto, setUltimoImpacto] =
    useState<InsumoDisponibilidadeResponse | null>(null);

  // Novo insumo
  const [nome, setNome] = useState('');
  const [unidadeMedida, setUnidadeMedida] = useState('ML');
  const [quantidadeAtual, setQuantidadeAtual] = useState(1000);
  const [estoqueMinimo, setEstoqueMinimo] = useState(250);
  const [motivoPausaModal, setMotivoPausaModal] = useState<{
    insumo: Insumo;
    motivo: string;
  } | null>(null);

  const carregarInsumos = useCallback(async () => {
    setLoading(true);
    setErrorMsg(null);
    try {
      const lista = await apiRequest<Insumo[]>({
        method: 'GET',
        url: '/catalogo/insumos',
      });
      setInsumos(lista);
    } catch (err) {
      setErrorMsg(parseApiError(err).message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    carregarInsumos();
  }, [carregarInsumos]);

  // Listener STOMP em tempo real (/topic/lojas/{lojaId}/disponibilidade)
  useEffect(() => {
    const lojaId = user?.lojaId || '019325f0-8a00-7000-8000-000000000001';
    return stompManager.subscribe<CatalogoEventoStomp>(
      `/topic/lojas/${lojaId}/disponibilidade`,
      () => {
        carregarInsumos();
      }
    );
  }, [user?.lojaId, carregarInsumos]);

  const handleCriarInsumo = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg(null);
    try {
      await apiRequest<Insumo>({
        method: 'POST',
        url: '/catalogo/insumos',
        data: {
          nome,
          unidadeMedida,
          quantidadeAtual,
          estoqueMinimo,
          disponivel: true,
        },
      });
      setNome('');
      await carregarInsumos();
    } catch (err) {
      setErrorMsg(parseApiError(err).message);
    }
  };

  const executarToggleDisponibilidade = async (
    insumo: Insumo,
    novoDisponivel: boolean,
    motivo?: string
  ) => {
    setErrorMsg(null);
    try {
      const resp = await apiRequest<InsumoDisponibilidadeResponse>({
        method: 'PATCH',
        url: `/catalogo/insumos/${insumo.id}/disponibilidade`,
        data: {
          disponivel: novoDisponivel,
          motivo: novoDisponivel
            ? null
            : motivo || 'Esgotado temporariamente no turno (86)',
        },
      });
      setUltimoImpacto(resp);
      setMotivoPausaModal(null);
      await carregarInsumos();
    } catch (err) {
      setErrorMsg(parseApiError(err).message);
    }
  };

  return (
    <div className="space-y-6">
      {/* Cabeçalho */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-white p-6 rounded-2xl border border-slate-200 shadow-sm">
        <div>
          <div className="flex items-center gap-2 text-amber-700 text-xs font-bold uppercase tracking-wider">
            <PackageCheck className="w-4 h-4" />
            <span>Controle Rápido de Ruptura (86) &amp; Broadcast STOMP</span>
          </div>
          <h1 className="text-2xl font-extrabold text-slate-900 mt-1">
            Gestão de Insumos &amp; Disponibilidade em Cascata
          </h1>
          <p className="text-xs text-slate-500 mt-1">
            Ao pausar um insumo (`PATCH /api/v1/catalogo/insumos/&#123;id&#125;/disponibilidade`),
            todos os produtos, adicionais e combos dependentes são pausados em cascata e
            notificados via WebSocket STOMP.
          </p>
        </div>
        <button
          type="button"
          onClick={carregarInsumos}
          className="self-start sm:self-auto px-4 py-2.5 rounded-xl border border-slate-200 hover:bg-slate-50 text-xs font-bold text-slate-700 flex items-center gap-2"
        >
          <RefreshCw className="w-4 h-4" />
          <span>Atualizar</span>
        </button>
      </div>

      {errorMsg && (
        <div className="rounded-2xl bg-rose-50 border border-rose-200 p-4 text-xs font-semibold text-rose-800 flex items-center gap-2">
          <AlertTriangle className="w-4 h-4 shrink-0" />
          <span>{errorMsg}</span>
        </div>
      )}

      {/* Resumo de Impacto em Cascata da Última Alteração */}
      {ultimoImpacto && (
        <div className="rounded-2xl bg-amber-50 border border-amber-200 p-5 space-y-3">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2 text-amber-900 font-bold text-sm">
              <Zap className="w-4 h-4 text-amber-600" />
              <span>
                Cascata Aplicada — Catálogo Versão #{ultimoImpacto.catalogoVersao}:{' '}
                {ultimoImpacto.insumo.nome} agora está{' '}
                {ultimoImpacto.insumo.disponivel ? 'DISPONÍVEL' : 'ESGOTADO (86)'}
              </span>
            </div>
            <button
              type="button"
              onClick={() => setUltimoImpacto(null)}
              className="text-xs font-semibold text-amber-800 underline"
            >
              Ocultar
            </button>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3 text-xs">
            <div className="bg-white/80 rounded-xl p-3 border border-amber-200/70">
              <span className="font-bold text-slate-700 block mb-1">
                Produtos Afetados ({ultimoImpacto.produtosAfetados.length}):
              </span>
              {ultimoImpacto.produtosAfetados.length === 0 ? (
                <span className="text-slate-500">Nenhum produto afetado diretamente.</span>
              ) : (
                <ul className="space-y-1">
                  {ultimoImpacto.produtosAfetados.map((p) => (
                    <li key={p.id} className="flex items-center justify-between">
                      <span className="font-medium text-slate-800">{p.nome}</span>
                      <span
                        className={`px-2 py-0.5 rounded font-bold text-[10px] ${
                          p.disponivel
                            ? 'bg-emerald-100 text-emerald-800'
                            : 'bg-rose-100 text-rose-800'
                        }`}
                      >
                        {p.disponivel ? 'DISPONÍVEL' : 'PAUSADO EM CASCATA'}
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </div>
            <div className="bg-white/80 rounded-xl p-3 border border-amber-200/70">
              <span className="font-bold text-slate-700 block mb-1">
                Modificadores Afetados ({ultimoImpacto.modificadoresAfetados.length}):
              </span>
              {ultimoImpacto.modificadoresAfetados.length === 0 ? (
                <span className="text-slate-500">
                  Nenhum modificador afetado diretamente.
                </span>
              ) : (
                <ul className="space-y-1">
                  {ultimoImpacto.modificadoresAfetados.map((m) => (
                    <li key={m.id} className="flex items-center justify-between">
                      <span className="font-medium text-slate-800">{m.nome}</span>
                      <span
                        className={`px-2 py-0.5 rounded font-bold text-[10px] ${
                          m.disponivel
                            ? 'bg-emerald-100 text-emerald-800'
                            : 'bg-rose-100 text-rose-800'
                        }`}
                      >
                        {m.disponivel ? 'DISPONÍVEL' : 'PAUSADO EM CASCATA'}
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </div>
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Lista de Insumos com Toggle 86 */}
        <div className="lg:col-span-8 grid grid-cols-1 md:grid-cols-2 gap-4">
          {loading ? (
            <div className="col-span-2 bg-white rounded-2xl p-8 text-center text-sm text-slate-500 border border-slate-200">
              Carregando insumos...
            </div>
          ) : (
            insumos.map((ins) => (
              <div
                key={ins.id}
                className={`rounded-2xl border p-5 flex flex-col justify-between gap-4 transition shadow-sm ${
                  ins.disponivel
                    ? 'bg-white border-slate-200'
                    : 'bg-rose-50/60 border-rose-300'
                }`}
              >
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <span className="px-2 py-0.5 rounded-md bg-slate-100 text-slate-600 font-mono text-[10px] font-bold">
                      UNIDADE: {ins.unidadeMedida || ins.unidade} · v{ins.versao}
                    </span>
                    <h3 className="text-base font-bold text-slate-900 mt-1.5">
                      {ins.nome}
                    </h3>
                    {!ins.disponivel && ins.motivoIndisponibilidade && (
                      <p className="text-xs font-medium text-rose-700 mt-1">
                        Motivo: {ins.motivoIndisponibilidade}
                      </p>
                    )}
                  </div>
                  <span
                    className={`px-2.5 py-1 rounded-full text-xs font-bold flex items-center gap-1 shrink-0 ${
                      ins.disponivel
                        ? 'bg-emerald-100 text-emerald-800'
                        : 'bg-rose-600 text-white'
                    }`}
                  >
                    {ins.disponivel ? (
                      <>
                        <CheckCircle2 className="w-3.5 h-3.5" />
                        <span>Disponível</span>
                      </>
                    ) : (
                      <>
                        <XCircle className="w-3.5 h-3.5" />
                        <span>ESGOTADO (86)</span>
                      </>
                    )}
                  </span>
                </div>

                <div className="flex items-center justify-between pt-3 border-t border-slate-200/80">
                  <div className="text-xs text-slate-500">
                    Estoque ref.:{' '}
                    <strong className="text-slate-800">
                      {ins.quantidadeAtual} {ins.unidadeMedida || ins.unidade}
                    </strong>{' '}
                    (mín. {ins.estoqueMinimo})
                  </div>

                  {ins.disponivel ? (
                    <button
                      type="button"
                      onClick={() =>
                        setMotivoPausaModal({
                          insumo: ins,
                          motivo: 'Acabou no bar/cozinha durante o turno',
                        })
                      }
                      className="px-3.5 py-2 rounded-xl bg-rose-600 hover:bg-rose-700 text-white text-xs font-bold transition shadow-sm"
                    >
                      Pausar / Esgotar (86)
                    </button>
                  ) : (
                    <button
                      type="button"
                      onClick={() => executarToggleDisponibilidade(ins, true)}
                      className="px-3.5 py-2 rounded-xl bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-bold transition shadow-sm"
                    >
                      Reativar Insumo
                    </button>
                  )}
                </div>
              </div>
            ))
          )}
        </div>

        {/* Cadastro de Novo Insumo */}
        <div className="lg:col-span-4">
          <form
            onSubmit={handleCriarInsumo}
            className="bg-white rounded-2xl border border-slate-200 p-6 shadow-sm space-y-4"
          >
            <div className="flex items-center gap-2 text-slate-900 font-bold text-base">
              <Plus className="w-5 h-5 text-amber-600" />
              <span>Cadastrar Novo Insumo</span>
            </div>

            <div>
              <label className="block text-xs font-bold uppercase text-slate-600 mb-1">
                Nome do Insumo
              </label>
              <input
                type="text"
                required
                value={nome}
                onChange={(e) => setNome(e.target.value)}
                placeholder="Ex.: Chantilly Fresco Artesanal"
                className="w-full px-3.5 py-2.5 rounded-xl border border-slate-300 text-sm focus:border-amber-600 outline-none"
              />
            </div>

            <div className="grid grid-cols-3 gap-3">
              <div>
                <label className="block text-xs font-bold uppercase text-slate-600 mb-1">
                  Unidade
                </label>
                <select
                  value={unidadeMedida}
                  onChange={(e) => setUnidadeMedida(e.target.value)}
                  className="w-full px-3 py-2.5 rounded-xl border border-slate-300 text-sm"
                >
                  <option value="ML">ML</option>
                  <option value="G">G</option>
                  <option value="UN">UN</option>
                  <option value="KG">KG</option>
                  <option value="L">L</option>
                </select>
              </div>
              <div>
                <label className="block text-xs font-bold uppercase text-slate-600 mb-1">
                  Qtd. Atual
                </label>
                <input
                  type="number"
                  min={0}
                  value={quantidadeAtual}
                  onChange={(e) => setQuantidadeAtual(Number(e.target.value))}
                  className="w-full px-3 py-2.5 rounded-xl border border-slate-300 text-sm"
                />
              </div>
              <div>
                <label className="block text-xs font-bold uppercase text-slate-600 mb-1">
                  Est. Mín.
                </label>
                <input
                  type="number"
                  min={0}
                  value={estoqueMinimo}
                  onChange={(e) => setEstoqueMinimo(Number(e.target.value))}
                  className="w-full px-3 py-2.5 rounded-xl border border-slate-300 text-sm"
                />
              </div>
            </div>

            <button
              type="submit"
              className="w-full py-2.5 rounded-xl bg-slate-900 hover:bg-slate-800 text-white text-xs font-bold transition"
            >
              Salvar Insumo na Loja
            </button>
          </form>
        </div>
      </div>

      {/* Modal de Confirmação de Pausa (86) */}
      {motivoPausaModal && (
        <div className="fixed inset-0 z-40 bg-slate-950/60 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl max-w-md w-full p-6 space-y-4 shadow-xl border border-slate-200">
            <h3 className="text-lg font-bold text-slate-900">
              Marcar Insumo como Esgotado (86)
            </h3>
            <p className="text-xs text-slate-600">
              Insumo: <strong>{motivoPausaModal.insumo.nome}</strong>. Todos os
              produtos e adicionais que dependem obrigatoriamente deste insumo serão
              indisponibilizados imediatamente no PDV e nos aplicativos Android.
            </p>
            <div>
              <label className="block text-xs font-bold uppercase text-slate-600 mb-1">
                Motivo da Indisponibilidade
              </label>
              <input
                type="text"
                value={motivoPausaModal.motivo}
                onChange={(e) =>
                  setMotivoPausaModal({
                    ...motivoPausaModal,
                    motivo: e.target.value,
                  })
                }
                className="w-full px-3.5 py-2.5 rounded-xl border border-slate-300 text-sm"
              />
            </div>
            <div className="flex justify-end gap-2 pt-2">
              <button
                type="button"
                onClick={() => setMotivoPausaModal(null)}
                className="px-4 py-2 rounded-xl border border-slate-300 text-xs font-bold text-slate-700"
              >
                Cancelar
              </button>
              <button
                type="button"
                onClick={() =>
                  executarToggleDisponibilidade(
                    motivoPausaModal.insumo,
                    false,
                    motivoPausaModal.motivo
                  )
                }
                className="px-4 py-2 rounded-xl bg-rose-600 hover:bg-rose-700 text-white text-xs font-bold"
              >
                Confirmar Esgotamento (86)
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
