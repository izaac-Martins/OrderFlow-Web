import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Coffee,
  Lock,
  Mail,
  Building2,
  ShieldCheck,
  ArrowRight,
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { parseApiError } from '../lib/errors';

const LOJAS_DISPONIVEIS = [
  {
    id: '019325f0-8a00-7000-8000-000000000001',
    codigo: 'MATRIZ-01',
    nome: 'Cafeteria OrderFlow — Unidade Matriz Jardins',
  },
  {
    id: '019325f0-8a00-7000-8000-000000000002',
    codigo: 'FILIAL-02',
    nome: 'Cafeteria OrderFlow — Filial Paulista',
  },
];

export const LoginPage: React.FC = () => {
  const { login, simulationMode, toggleSimulationMode } = useAuth();
  const navigate = useNavigate();

  const [email, setEmail] = useState('gerente@orderflow.local');
  const [senha, setSenha] = useState('123456');
  const [lojaSelecionada, setLojaSelecionada] = useState(LOJAS_DISPONIVEIS[0]);
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setErrorMsg(null);

    try {
      await login({
        email,
        senha,
        lojaId: lojaSelecionada.id,
        codigoLoja: lojaSelecionada.codigo,
        dispositivoId: 'WEB-MASTER-01',
      });
      navigate('/pdv', { replace: true });
    } catch (err) {
      setErrorMsg(parseApiError(err).message);
    } finally {
      setLoading(false);
    }
  };

  const quickLogin = async (presetEmail: string) => {
    setEmail(presetEmail);
    setSenha('123456');
    setLoading(true);
    setErrorMsg(null);
    try {
      await login({
        email: presetEmail,
        senha: '123456',
        lojaId: lojaSelecionada.id,
        codigoLoja: lojaSelecionada.codigo,
      });
      navigate('/pdv', { replace: true });
    } catch (err) {
      setErrorMsg(parseApiError(err).message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-950 flex items-center justify-center p-4">
      <div className="w-full max-w-4xl grid grid-cols-1 lg:grid-cols-12 rounded-3xl overflow-hidden shadow-2xl border border-slate-800 bg-slate-900">
        {/* Coluna Institucional */}
        <div className="lg:col-span-5 bg-gradient-to-br from-amber-600 via-amber-700 to-stone-900 p-8 text-white flex flex-col justify-between">
          <div className="space-y-6">
            <div className="w-12 h-12 rounded-2xl bg-white/15 backdrop-blur flex items-center justify-center">
              <Coffee className="w-7 h-7 text-white" />
            </div>
            <div>
              <span className="text-xs font-bold uppercase tracking-widest text-amber-200">
                Ecossistema OrderFlow
              </span>
              <h1 className="text-2xl font-extrabold mt-1 leading-tight">
                Sistema Master &amp; PDV Gerencial
              </h1>
              <p className="text-sm text-amber-100/90 mt-3 leading-relaxed">
                Retaguarda multi-loja com catálogo em centavos inteiros, controle
                em tempo real via STOMP (86), operação de caixa com fechamento
                cego e alçada por PIN.
              </p>
            </div>
          </div>

          <div className="space-y-3 pt-8 border-t border-white/15 text-xs text-amber-100">
            <div className="flex items-center gap-2">
              <ShieldCheck className="w-4 h-4 shrink-0 text-amber-300" />
              <span>RBAC Granular + PIN de Alçada (4321 / 1234)</span>
            </div>
            <div className="flex items-center justify-between pt-2">
              <span>Modo de Conexão:</span>
              <button
                type="button"
                onClick={() => toggleSimulationMode(!simulationMode)}
                className="px-2.5 py-1 rounded-lg bg-black/30 hover:bg-black/50 font-semibold text-white transition"
              >
                {simulationMode ? 'Simulação Local Ativa' : 'API Spring (:8080)'}
              </button>
            </div>
          </div>
        </div>

        {/* Coluna do Formulário */}
        <div className="lg:col-span-7 p-8 lg:p-10 bg-white text-slate-900 flex flex-col justify-center">
          <h2 className="text-xl font-bold text-slate-900">
            Autenticação de Operador / Gestor
          </h2>
          <p className="text-xs text-slate-500 mt-1">
            Selecione a unidade operacional e informe suas credenciais.
          </p>

          <form onSubmit={handleSubmit} className="mt-6 space-y-4">
            <div>
              <label className="block text-xs font-bold uppercase tracking-wider text-slate-600 mb-1.5">
                Unidade / Loja (`X-Loja-Id`)
              </label>
              <div className="relative">
                <Building2 className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
                <select
                  value={lojaSelecionada.id}
                  onChange={(e) => {
                    const found = LOJAS_DISPONIVEIS.find(
                      (l) => l.id === e.target.value
                    );
                    if (found) setLojaSelecionada(found);
                  }}
                  className="w-full pl-10 pr-4 py-2.5 rounded-xl border border-slate-300 text-sm font-medium focus:border-amber-600 outline-none"
                >
                  {LOJAS_DISPONIVEIS.map((l) => (
                    <option key={l.id} value={l.id}>
                      [{l.codigo}] {l.nome}
                    </option>
                  ))}
                </select>
              </div>
            </div>

            <div>
              <label className="block text-xs font-bold uppercase tracking-wider text-slate-600 mb-1.5">
                E-mail Corporativo
              </label>
              <div className="relative">
                <Mail className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
                <input
                  type="email"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="w-full pl-10 pr-4 py-2.5 rounded-xl border border-slate-300 text-sm focus:border-amber-600 outline-none"
                />
              </div>
            </div>

            <div>
              <label className="block text-xs font-bold uppercase tracking-wider text-slate-600 mb-1.5">
                Senha
              </label>
              <div className="relative">
                <Lock className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
                <input
                  type="password"
                  required
                  value={senha}
                  onChange={(e) => setSenha(e.target.value)}
                  className="w-full pl-10 pr-4 py-2.5 rounded-xl border border-slate-300 text-sm focus:border-amber-600 outline-none"
                />
              </div>
            </div>

            {errorMsg && (
              <div className="rounded-xl bg-rose-50 border border-rose-200 p-3 text-xs font-medium text-rose-700">
                {errorMsg}
              </div>
            )}

            <button
              type="submit"
              disabled={loading}
              className="w-full py-3 rounded-xl bg-amber-600 hover:bg-amber-700 disabled:opacity-50 text-white font-bold text-sm flex items-center justify-center gap-2 shadow-md transition"
            >
              <span>{loading ? 'Autenticando...' : 'Entrar no Sistema Master'}</span>
              <ArrowRight className="w-4 h-4" />
            </button>
          </form>

          {/* Atalhos Rápidos de Perfis para Homologação */}
          <div className="mt-7 pt-5 border-t border-slate-200">
            <span className="block text-[11px] font-bold uppercase tracking-wider text-slate-400 mb-2.5">
              Acesso Rápido por Perfil (Homologação RBAC)
            </span>
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
              {[
                { label: 'GERENTE', email: 'gerente@orderflow.local' },
                { label: 'ADMIN', email: 'admin@orderflow.local' },
                { label: 'OPERADOR CAIXA', email: 'caixa@orderflow.local' },
                { label: 'ATENDENTE', email: 'atendente@orderflow.local' },
              ].map((p) => (
                <button
                  key={p.label}
                  type="button"
                  onClick={() => quickLogin(p.email)}
                  className="px-3 py-2 rounded-xl border border-slate-200 hover:border-amber-500 hover:bg-amber-50 text-xs font-bold text-slate-700 transition"
                >
                  {p.label}
                </button>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
