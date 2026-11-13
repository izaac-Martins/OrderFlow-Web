import React, { useCallback, useEffect, useState } from 'react';
import {
  Wallet,
  Lock,
  ArrowUpCircle,
  ArrowDownCircle,
  ShieldAlert,
  CheckCircle2,
  AlertTriangle,
  FileCheck2,
  RotateCcw,
  EyeOff,
  Plus,
} from 'lucide-react';
import { apiRequest } from '../lib/http';
import { parseApiError } from '../lib/errors';
import {
  formatCentavos,
  formatDateTimeBr,
  parseReaisToCentavos,
} from '../lib/formatters';
import { useAuth } from '../context/AuthContext';
import { useManagerPin } from '../context/ManagerPinContext';
import type {
  ConferenciaResponse,
  FechamentoProtocoloResponse,
  MetodoPagamento,
  MovimentacaoGerencial,
  MovimentacaoOperador,
  SessaoCaixaGerencial,
  SessaoCaixaOperador,
  TerminalCaixa,
} from '../types/api';

const METODOS_DECLARACAO: Array<{
  metodo: MetodoPagamento;
  label: string;
  obrigatorio?: boolean;
  exigeComprovantes?: boolean;
}> = [
  { metodo: 'DINHEIRO', label: 'Dinheiro em Espécie (Gaveta)', obrigatorio: true },
  {
    metodo: 'CARTAO_CREDITO',
    label: 'Cartão de Crédito (POS/TEF)',
    exigeComprovantes: true,
  },
  {
    metodo: 'CARTAO_DEBITO',
    label: 'Cartão de Débito (POS/TEF)',
    exigeComprovantes: true,
  },
  { metodo: 'PIX', label: 'PIX QR Code / Chave' },
  { metodo: 'VOUCHER', label: 'Voucher Refeição / Alimentação' },
];

export const CaixaPage: React.FC = () => {
  const { isManagerOrAdmin } = useAuth();
  const { requestManagerPin } = useManagerPin();

  const [abaAtiva, setAbaAtiva] = useState<'operador' | 'gerencial'>('operador');
  const [terminais, setTerminais] = useState<TerminalCaixa[]>([]);
  const [sessoesGerenciais, setSessoesGerenciais] = useState<
    SessaoCaixaGerencial[]
  >([]);
  const [sessaoAbertaAtual, setSessaoAbertaAtual] =
    useState<SessaoCaixaOperador | null>(null);
  const [ultimasMovsOperador, setUltimasMovsOperador] = useState<
    MovimentacaoOperador[]
  >([]);

  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  // Abertura de Caixa
  const [caixaSelecionadoId, setCaixaSelecionadoId] = useState('');
  const [fundoTrocoReais, setFundoTrocoReais] = useState('200,00');
  const [novoTerminalNome, setNovoTerminalNome] = useState('');

  // Lançamento de Sangria / Suprimento
  const [tipoMov, setTipoMov] = useState<'SANGRIA' | 'SUPRIMENTO'>('SANGRIA');
  const [valorMovReais, setValorMovReais] = useState('150,00');
  const [motivoMov, setMotivoMov] = useState('Recolhimento parcial para cofre');

  // Fechamento Cego
  const [valoresDeclarados, setValoresDeclarados] = useState<
    Record<MetodoPagamento, string>
  >({
    DINHEIRO: '200,00',
    CARTAO_CREDITO: '0,00',
    CARTAO_DEBITO: '0,00',
    PIX: '0,00',
    VOUCHER: '0,00',
  });
  const [comprovantesDeclarados, setComprovantesDeclarados] = useState<
    Record<string, number>
  >({
    CARTAO_CREDITO: 0,
    CARTAO_DEBITO: 0,
  });
  const [obsFechamento, setObsFechamento] = useState('');
  const [protocoloGerado, setProtocoloGerado] =
    useState<FechamentoProtocoloResponse | null>(null);

  // Conferência Gerencial
  const [sessaoInspecionadaId, setSessaoInspecionadaId] = useState<
    string | null
  >(null);
  const [conferenciaDetalhe, setConferenciaDetalhe] =
    useState<ConferenciaResponse | null>(null);
  const [movimentacoesGerenciais, setMovimentacoesGerenciais] = useState<
    MovimentacaoGerencial[]
  >([]);
  const [obsConferencia, setObsConferencia] = useState('');

  const carregarCaixa = useCallback(async () => {
    try {
      const [terms, sessoes] = await Promise.all([
        apiRequest<TerminalCaixa[]>({ method: 'GET', url: '/caixas' }),
        apiRequest<SessaoCaixaGerencial[]>({
          method: 'GET',
          url: '/caixa/sessoes',
        }),
      ]);
      setTerminais(terms);
      setSessoesGerenciais(sessoes);
      if (!caixaSelecionadoId && terms.length > 0) {
        setCaixaSelecionadoId(terms[0].id);
      }

      const aberta = sessoes.find((s) => s.status === 'ABERTA');
      if (aberta) {
        setSessaoAbertaAtual({
          id: aberta.id,
          caixaId: aberta.caixaId,
          caixaNome: aberta.caixaNome,
          operador: aberta.operador,
          status: aberta.status,
          abertaEm: aberta.abertaEm,
          dataAbertura: aberta.abertaEm,
          fundoTrocoCentavos: aberta.fundoTrocoCentavos,
          quantidadePagamentos: 0,
          pedidosPosPagoEmAberto: 0,
          versao: aberta.versao,
        });
      } else {
        setSessaoAbertaAtual(null);
      }
    } catch (err) {
      setErrorMsg(parseApiError(err).message);
    }
  }, [caixaSelecionadoId]);

  useEffect(() => {
    carregarCaixa();
  }, [carregarCaixa]);

  const handleCriarTerminal = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!novoTerminalNome.trim()) return;
    setErrorMsg(null);
    try {
      const criado = await apiRequest<TerminalCaixa>({
        method: 'POST',
        url: '/caixas',
        data: {
          nome: novoTerminalNome,
          impressoraIp: '192.168.1.50',
          impressoraPorta: 9100,
          possuiGaveta: true,
        },
      });
      setNovoTerminalNome('');
      setCaixaSelecionadoId(criado.id);
      await carregarCaixa();
    } catch (err) {
      setErrorMsg(parseApiError(err).message);
    }
  };

  const handleAbrirSessao = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg(null);
    setSuccessMsg(null);
    setProtocoloGerado(null);
    try {
      const resp = await apiRequest<SessaoCaixaOperador>({
        method: 'POST',
        url: '/caixa/abertura',
        data: {
          caixaId: caixaSelecionadoId,
          fundoTrocoCentavos: parseReaisToCentavos(fundoTrocoReais),
        },
      });
      setSessaoAbertaAtual(resp);
      setUltimasMovsOperador([]);
      setSuccessMsg(
        `Sessão aberta com sucesso no terminal '${resp.caixaNome}' com fundo de troco de ${formatCentavos(
          resp.fundoTrocoCentavos
        )}.`
      );
      await carregarCaixa();
    } catch (err) {
      setErrorMsg(parseApiError(err).message);
    }
  };

  const submeterMovimentacao = async (pinAprovador?: string) => {
    if (!sessaoAbertaAtual) return;
    const valorCentavos = parseReaisToCentavos(valorMovReais);

    const resp = await apiRequest<MovimentacaoOperador>({
      method: 'POST',
      url: '/caixa/movimentacoes',
      data: {
        sessaoCaixaId: sessaoAbertaAtual.id,
        tipo: tipoMov,
        valorCentavos,
        motivo: motivoMov,
        pinAprovador: pinAprovador || null,
      },
    });

    setUltimasMovsOperador((prev) => [resp, ...prev]);
    setSuccessMsg(
      `${resp.tipo} de ${formatCentavos(resp.valorCentavos)} registrada com sucesso${
        resp.aprovadoPor ? ` (Autorizado por ${resp.aprovadoPor.nome})` : ''
      }.`
    );
    await carregarCaixa();
  };

  const handleRegistrarMovimentacao = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg(null);
    setSuccessMsg(null);

    try {
      await submeterMovimentacao();
    } catch (err) {
      const apiErr = parseApiError(err);
      if (apiErr.code === 'APROVACAO_NECESSARIA') {
        const pinResult = await requestManagerPin({
          title: 'Autorizar Sangria Acima do Limite',
          description: apiErr.message,
          operationContext: `Sangria de ${formatCentavos(
            parseReaisToCentavos(valorMovReais)
          )} — Motivo: ${motivoMov}`,
        });
        if (pinResult) {
          try {
            await submeterMovimentacao(pinResult.pin);
          } catch (retryErr) {
            setErrorMsg(parseApiError(retryErr).message);
          }
        }
        return;
      }
      setErrorMsg(apiErr.message);
    }
  };

  const handleFechamentoCego = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!sessaoAbertaAtual) return;
    setErrorMsg(null);
    setSuccessMsg(null);

    const declaracoes = METODOS_DECLARACAO.map((m) => ({
      metodo: m.metodo,
      valorDeclaradoCentavos: parseReaisToCentavos(
        valoresDeclarados[m.metodo] || '0'
      ),
      quantidadeComprovantes: m.exigeComprovantes
        ? comprovantesDeclarados[m.metodo] || 0
        : null,
    })).filter(
      (d) => d.metodo === 'DINHEIRO' || d.valorDeclaradoCentavos > 0
    );

    try {
      const protocolo = await apiRequest<FechamentoProtocoloResponse>({
        method: 'POST',
        url: '/caixa/fechamento',
        data: {
          sessaoCaixaId: sessaoAbertaAtual.id,
          versao: sessaoAbertaAtual.versao,
          observacao: obsFechamento || null,
          declaracoes,
        },
      });
      setProtocoloGerado(protocolo);
      setSessaoAbertaAtual(null);
      setSuccessMsg(
        `Fechamento Cego concluído! Protocolo gerado: ${protocolo.protocolo}`
      );
      await carregarCaixa();
    } catch (err) {
      setErrorMsg(parseApiError(err).message);
    }
  };

  const inspecionarSessaoGerencial = async (sessao: SessaoCaixaGerencial) => {
    setSessaoInspecionadaId(sessao.id);
    setErrorMsg(null);
    try {
      const movs = await apiRequest<MovimentacaoGerencial[]>({
        method: 'GET',
        url: `/caixa/sessoes/${sessao.id}/movimentacoes`,
      });
      setMovimentacoesGerenciais(movs);

      if (sessao.status !== 'ABERTA') {
        const conf = await apiRequest<ConferenciaResponse>({
          method: 'GET',
          url: `/caixa/sessoes/${sessao.id}/conferencia`,
        });
        setConferenciaDetalhe(conf);
      } else {
        setConferenciaDetalhe(null);
      }
    } catch (err) {
      setErrorMsg(parseApiError(err).message);
    }
  };

  const handleHomologarConferencia = async (sessaoId: string) => {
    setErrorMsg(null);
    try {
      const conf = await apiRequest<ConferenciaResponse>({
        method: 'POST',
        url: `/caixa/sessoes/${sessaoId}/conferir`,
        data: {
          observacao: obsConferencia || 'Conferência homologada pela gerência.',
        },
      });
      setConferenciaDetalhe(conf);
      setSuccessMsg('Sessão de caixa homologada como CONFERIDA.');
      await carregarCaixa();
    } catch (err) {
      setErrorMsg(parseApiError(err).message);
    }
  };

  const handleEstornarMovimentacao = async (mov: MovimentacaoGerencial) => {
    setErrorMsg(null);
    try {
      await apiRequest<MovimentacaoGerencial>({
        method: 'POST',
        url: `/caixa/movimentacoes/${mov.id}/estorno`,
        data: {
          motivo: `Estorno gerencial reverso ref. ${mov.tipo}`,
        },
      });
      setSuccessMsg('Lançamento reverso imutável registrado com sucesso.');
      const sessao = sessoesGerenciais.find((s) => s.id === mov.sessaoCaixaId);
      if (sessao) {
        await inspecionarSessaoGerencial(sessao);
      }
    } catch (err) {
      setErrorMsg(parseApiError(err).message);
    }
  };

  return (
    <div className="space-y-6">
      {/* Cabeçalho & Seletor de Visão (Operador Cego vs Conferência Gerencial) */}
      <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2 text-amber-700 text-xs font-bold uppercase tracking-wider">
            <Wallet className="w-4 h-4" />
            <span>Módulo Financeiro Imutável &amp; Fechamento Cego</span>
          </div>
          <h1 className="text-2xl font-extrabold text-slate-900 mt-1">
            Operação de Caixa, Sangria com PIN &amp; Conferência Gerencial
          </h1>
        </div>

        <div className="flex items-center gap-2 bg-slate-100 p-1.5 rounded-xl">
          <button
            type="button"
            onClick={() => setAbaAtiva('operador')}
            className={`px-4 py-2 rounded-lg text-xs font-bold transition ${
              abaAtiva === 'operador'
                ? 'bg-white text-slate-900 shadow-sm'
                : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            Visão do Operador (Cega)
          </button>
          <button
            type="button"
            onClick={() => setAbaAtiva('gerencial')}
            className={`px-4 py-2 rounded-lg text-xs font-bold transition flex items-center gap-1.5 ${
              abaAtiva === 'gerencial'
                ? 'bg-amber-600 text-white shadow-sm'
                : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            <Lock className="w-3.5 h-3.5" />
            <span>Conferência &amp; Auditoria (Gerente)</span>
          </button>
        </div>
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

      {/* Protocolo de Fechamento Cego Recém-Emitido */}
      {protocoloGerado && (
        <div className="rounded-2xl bg-slate-900 text-white p-6 border border-slate-800 shadow-lg flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div className="space-y-1">
            <span className="text-xs font-bold uppercase tracking-wider text-amber-400">
              Comprovante de Fechamento Cego Emitido
            </span>
            <h3 className="text-xl font-mono font-extrabold">
              Protocolo: {protocoloGerado.protocolo}
            </h3>
            <p className="text-xs text-slate-300">
              Status: <strong>{protocoloGerado.status}</strong> · Encerrado em{' '}
              {formatDateTimeBr(protocoloGerado.fechadaEm)}. Conforme a regra de
              fechamento cego, o saldo esperado e eventuais diferenças estão
              disponíveis exclusivamente na aba de Conferência Gerencial.
            </p>
          </div>
          {isManagerOrAdmin && (
            <button
              type="button"
              onClick={() => {
                setAbaAtiva('gerencial');
                const sessao = sessoesGerenciais.find(
                  (s) => s.id === protocoloGerado.sessaoId
                );
                if (sessao) inspecionarSessaoGerencial(sessao);
              }}
              className="px-4 py-2.5 rounded-xl bg-amber-500 hover:bg-amber-400 text-slate-950 text-xs font-bold shrink-0"
            >
              Abrir Conferência Gerencial
            </button>
          )}
        </div>
      )}

      {/* ABA 1: VISÃO DO OPERADOR (ABERTURA, SUPRIMENTO/SANGRIA E FECHAMENTO CEGO) */}
      {abaAtiva === 'operador' && (
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
          {/* Coluna Esquerda: Status da Sessão / Abertura */}
          <div className="lg:col-span-5 space-y-6">
            {!sessaoAbertaAtual ? (
              <div className="bg-white rounded-2xl border border-slate-200 p-6 shadow-sm space-y-5">
                <div>
                  <span className="text-xs font-bold uppercase text-amber-700">
                    Turno Fechado
                  </span>
                  <h2 className="text-lg font-extrabold text-slate-900">
                    Abrir Nova Sessão de Caixa (`POST /api/v1/caixa/abertura`)
                  </h2>
                  <p className="text-xs text-slate-500 mt-1">
                    O sistema impede mais de uma sessão aberta simultânea por
                    terminal (`409 CAIXA_JA_ABERTO`) ou por operador (`409
                    OPERADOR_COM_SESSAO`).
                  </p>
                </div>

                <form onSubmit={handleAbrirSessao} className="space-y-4">
                  <div>
                    <label className="block text-xs font-bold uppercase text-slate-600 mb-1">
                      Terminal de Caixa Físico
                    </label>
                    <select
                      value={caixaSelecionadoId}
                      onChange={(e) => setCaixaSelecionadoId(e.target.value)}
                      className="w-full px-3.5 py-2.5 rounded-xl border border-slate-300 text-sm font-medium"
                    >
                      {terminais.map((t) => (
                        <option key={t.id} value={t.id}>
                          {t.nome}{' '}
                          {t.sessaoAbertaId ? '(SESSÃO ABERTA)' : '(Disponível)'}
                        </option>
                      ))}
                    </select>
                  </div>

                  <div>
                    <label className="block text-xs font-bold uppercase text-slate-600 mb-1">
                      Fundo de Troco Inicial Contado (R$)
                    </label>
                    <input
                      type="text"
                      required
                      value={fundoTrocoReais}
                      onChange={(e) => setFundoTrocoReais(e.target.value)}
                      className="w-full px-3.5 py-2.5 rounded-xl border border-slate-300 text-base font-bold"
                    />
                  </div>

                  <button
                    type="submit"
                    className="w-full py-3 rounded-xl bg-amber-600 hover:bg-amber-700 text-white text-sm font-bold shadow-sm transition"
                  >
                    Abrir Sessão de Caixa Agora
                  </button>
                </form>

                {isManagerOrAdmin && (
                  <form
                    onSubmit={handleCriarTerminal}
                    className="pt-4 border-t border-slate-200 flex gap-2"
                  >
                    <input
                      type="text"
                      value={novoTerminalNome}
                      onChange={(e) => setNovoTerminalNome(e.target.value)}
                      placeholder="Novo terminal (ex.: PDV 03 Terraço)"
                      className="flex-1 px-3 py-2 rounded-xl border border-slate-300 text-xs"
                    />
                    <button
                      type="submit"
                      className="px-3 py-2 rounded-xl bg-slate-900 text-white text-xs font-bold flex items-center gap-1"
                    >
                      <Plus className="w-3.5 h-3.5" />
                      <span>Criar</span>
                    </button>
                  </form>
                )}
              </div>
            ) : (
              <div className="bg-white rounded-2xl border border-emerald-300 p-6 shadow-sm space-y-5">
                <div className="flex items-start justify-between">
                  <div>
                    <span className="px-2.5 py-1 rounded-full bg-emerald-100 text-emerald-800 text-xs font-bold">
                      SESSÃO ABERTA · v{sessaoAbertaAtual.versao}
                    </span>
                    <h2 className="text-lg font-extrabold text-slate-900 mt-2">
                      {sessaoAbertaAtual.caixaNome}
                    </h2>
                    <p className="text-xs text-slate-500">
                      Operador titular:{' '}
                      <strong>{sessaoAbertaAtual.operador.nome}</strong>
                    </p>
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-3 bg-slate-50 p-4 rounded-xl border border-slate-200 text-xs">
                  <div>
                    <span className="text-slate-500 block">
                      Fundo de Troco Inicial
                    </span>
                    <strong className="text-base text-slate-900">
                      {formatCentavos(sessaoAbertaAtual.fundoTrocoCentavos)}
                    </strong>
                  </div>
                  <div>
                    <span className="text-slate-500 block">
                      Saldo Esperado na Gaveta
                    </span>
                    <span className="inline-flex items-center gap-1 font-bold text-amber-800 mt-0.5">
                      <EyeOff className="w-3.5 h-3.5" />
                      <span>Oculto (Fechamento Cego)</span>
                    </span>
                  </div>
                </div>

                {/* Formulário de Suprimento e Sangria (Integrado ao PIN de Alçada) */}
                <form
                  onSubmit={handleRegistrarMovimentacao}
                  className="pt-4 border-t border-slate-200 space-y-4"
                >
                  <div className="flex items-center justify-between">
                    <h3 className="text-sm font-bold text-slate-900">
                      Registrar Sangria ou Suprimento
                    </h3>
                    <span className="text-[11px] text-slate-500">
                      Limite s/ PIN: R$ 500,00
                    </span>
                  </div>

                  <div className="grid grid-cols-2 gap-2">
                    <button
                      type="button"
                      onClick={() => setTipoMov('SANGRIA')}
                      className={`py-2.5 rounded-xl text-xs font-bold flex items-center justify-center gap-1.5 border transition ${
                        tipoMov === 'SANGRIA'
                          ? 'bg-rose-600 text-white border-rose-600'
                          : 'bg-white text-slate-700 border-slate-300'
                      }`}
                    >
                      <ArrowDownCircle className="w-4 h-4" />
                      <span>SANGRIA (Saída)</span>
                    </button>
                    <button
                      type="button"
                      onClick={() => setTipoMov('SUPRIMENTO')}
                      className={`py-2.5 rounded-xl text-xs font-bold flex items-center justify-center gap-1.5 border transition ${
                        tipoMov === 'SUPRIMENTO'
                          ? 'bg-emerald-600 text-white border-emerald-600'
                          : 'bg-white text-slate-700 border-slate-300'
                      }`}
                    >
                      <ArrowUpCircle className="w-4 h-4" />
                      <span>SUPRIMENTO (Entrada)</span>
                    </button>
                  </div>

                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    <div>
                      <label className="block text-[11px] font-bold uppercase text-slate-600 mb-1">
                        Valor em Espécie (R$)
                      </label>
                      <input
                        type="text"
                        required
                        value={valorMovReais}
                        onChange={(e) => setValorMovReais(e.target.value)}
                        className="w-full px-3 py-2 rounded-xl border border-slate-300 text-sm font-bold"
                      />
                    </div>
                    <div>
                      <label className="block text-[11px] font-bold uppercase text-slate-600 mb-1">
                        Motivo Obrigatório
                      </label>
                      <input
                        type="text"
                        required
                        minLength={5}
                        value={motivoMov}
                        onChange={(e) => setMotivoMov(e.target.value)}
                        className="w-full px-3 py-2 rounded-xl border border-slate-300 text-xs"
                      />
                    </div>
                  </div>

                  <button
                    type="submit"
                    className="w-full py-2.5 rounded-xl bg-slate-900 hover:bg-slate-800 text-white text-xs font-bold transition"
                  >
                    Confirmar Lançamento Imutável
                  </button>
                </form>

                {ultimasMovsOperador.length > 0 && (
                  <div className="pt-3 border-t border-slate-200 space-y-2">
                    <span className="text-[11px] font-bold uppercase text-slate-500 block">
                      Recibos Emitidos neste Turno
                    </span>
                    {ultimasMovsOperador.map((m) => (
                      <div
                        key={m.id}
                        className="p-2.5 rounded-xl bg-slate-50 border border-slate-200 text-xs flex items-center justify-between"
                      >
                        <div>
                          <span className="font-bold text-slate-800">
                            {m.tipo}
                          </span>{' '}
                          — <span className="text-slate-600">{m.motivo}</span>
                        </div>
                        <span className="font-bold text-slate-900">
                          {formatCentavos(m.valorCentavos)}
                        </span>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}
          </div>

          {/* Coluna Direita: Declaração de Fechamento Cego */}
          <div className="lg:col-span-7">
            <div className="bg-white rounded-2xl border border-slate-200 p-6 shadow-sm space-y-5">
              <div className="flex items-start justify-between gap-4">
                <div>
                  <span className="text-xs font-bold uppercase text-amber-700">
                    Encerramento de Turno às Cegas
                  </span>
                  <h2 className="text-lg font-extrabold text-slate-900">
                    Declaração de Fechamento Cego (`POST /api/v1/caixa/fechamento`)
                  </h2>
                  <p className="text-xs text-slate-500 mt-1">
                    Conte fisicamente as cédulas/moedas na gaveta e os comprovantes
                    de cartão/PIX. O sistema registrará sua contagem e emitirá o
                    protocolo sem exibir os valores esperados.
                  </p>
                </div>
              </div>

              <form onSubmit={handleFechamentoCego} className="space-y-4">
                <div className="space-y-3">
                  {METODOS_DECLARACAO.map((item) => (
                    <div
                      key={item.metodo}
                      className="grid grid-cols-1 sm:grid-cols-12 items-center gap-3 p-3.5 rounded-xl bg-slate-50 border border-slate-200"
                    >
                      <div className="sm:col-span-6">
                        <span className="text-xs font-bold text-slate-900 block">
                          {item.label}
                        </span>
                        <span className="text-[11px] font-mono text-slate-500">
                          {item.metodo}{' '}
                          {item.obrigatorio ? '· OBRIGATÓRIO' : '· Opcional'}
                        </span>
                      </div>
                      <div className="sm:col-span-4">
                        <label className="block text-[10px] font-bold uppercase text-slate-500 mb-0.5">
                          Valor Contado (R$)
                        </label>
                        <input
                          type="text"
                          required={item.obrigatorio}
                          value={valoresDeclarados[item.metodo]}
                          onChange={(e) =>
                            setValoresDeclarados({
                              ...valoresDeclarados,
                              [item.metodo]: e.target.value,
                            })
                          }
                          className="w-full px-3 py-1.5 rounded-lg border border-slate-300 text-sm font-bold bg-white"
                        />
                      </div>
                      <div className="sm:col-span-2">
                        {item.exigeComprovantes && (
                          <>
                            <label className="block text-[10px] font-bold uppercase text-slate-500 mb-0.5">
                              Fitas/Slips
                            </label>
                            <input
                              type="number"
                              min={0}
                              value={comprovantesDeclarados[item.metodo] || 0}
                              onChange={(e) =>
                                setComprovantesDeclarados({
                                  ...comprovantesDeclarados,
                                  [item.metodo]: Number(e.target.value),
                                })
                              }
                              className="w-full px-2.5 py-1.5 rounded-lg border border-slate-300 text-xs bg-white"
                            />
                          </>
                        )}
                      </div>
                    </div>
                  ))}
                </div>

                <div>
                  <label className="block text-xs font-bold uppercase text-slate-600 mb-1">
                    Observações do Operador (Opcional)
                  </label>
                  <input
                    type="text"
                    value={obsFechamento}
                    onChange={(e) => setObsFechamento(e.target.value)}
                    placeholder="Ex.: Uma nota de R$ 50,00 dilacerada separada no envelope"
                    className="w-full px-3.5 py-2.5 rounded-xl border border-slate-300 text-xs"
                  />
                </div>

                <button
                  type="submit"
                  disabled={!sessaoAbertaAtual}
                  className="w-full py-3 rounded-xl bg-amber-600 hover:bg-amber-700 disabled:opacity-40 text-white text-sm font-bold flex items-center justify-center gap-2 shadow-sm transition"
                >
                  <FileCheck2 className="w-4 h-4" />
                  <span>
                    {sessaoAbertaAtual
                      ? 'Concluir Fechamento Cego e Emitir Protocolo'
                      : 'Abra uma sessão de caixa para habilitar o fechamento'}
                  </span>
                </button>
              </form>
            </div>
          </div>
        </div>
      )}

      {/* ABA 2: CONFERÊNCIA GERENCIAL & AUDITORIA DE QUEBRAS / SINALIZAÇÕES */}
      {abaAtiva === 'gerencial' && (
        <div className="space-y-6">
          {!isManagerOrAdmin ? (
            <div className="bg-white rounded-2xl border border-rose-200 p-8 text-center space-y-3">
              <ShieldAlert className="w-10 h-10 text-rose-600 mx-auto" />
              <h3 className="text-lg font-bold text-slate-900">
                Área Exclusiva para GERENTE e ADMIN
              </h3>
              <p className="text-xs text-slate-600 max-w-md mx-auto">
                Para preservar o fechamento cego, operadores de caixa e atendentes
                não possuem acesso aos valores esperados, quebras ou sinalizações de
                saldo negativo. Use o seletor RBAC no topo da barra para alternar
                para <strong>GERENTE</strong>.
              </p>
            </div>
          ) : (
            <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
              {/* Lista de Sessões de Caixa da Loja */}
              <div className="lg:col-span-5 bg-white rounded-2xl border border-slate-200 p-5 shadow-sm space-y-4">
                <h2 className="text-base font-bold text-slate-900">
                  Histórico de Sessões da Loja (`GET /api/v1/caixa/sessoes`)
                </h2>
                {sessoesGerenciais.length === 0 ? (
                  <p className="text-xs text-slate-500">
                    Nenhuma sessão registrada ainda.
                  </p>
                ) : (
                  <div className="space-y-2.5">
                    {sessoesGerenciais.map((s) => (
                      <div
                        key={s.id}
                        onClick={() => inspecionarSessaoGerencial(s)}
                        className={`p-4 rounded-xl border cursor-pointer transition space-y-2 ${
                          sessaoInspecionadaId === s.id
                            ? 'border-amber-600 bg-amber-50/50'
                            : 'border-slate-200 hover:bg-slate-50'
                        }`}
                      >
                        <div className="flex items-center justify-between">
                          <span className="font-bold text-xs text-slate-900">
                            {s.caixaNome}
                          </span>
                          <div className="flex items-center gap-1.5">
                            {s.possuiSinalizacoes && (
                              <span className="px-2 py-0.5 rounded bg-rose-600 text-white text-[10px] font-bold">
                                SALDO_ESPERADO_NEGATIVO
                              </span>
                            )}
                            <span
                              className={`px-2 py-0.5 rounded text-[10px] font-bold ${
                                s.status === 'ABERTA'
                                  ? 'bg-emerald-100 text-emerald-800'
                                  : s.status === 'CONFERIDA'
                                  ? 'bg-sky-100 text-sky-800'
                                  : 'bg-amber-100 text-amber-900'
                              }`}
                            >
                              {s.status}
                            </span>
                          </div>
                        </div>
                        <div className="text-xs text-slate-600 flex justify-between">
                          <span>Operador: {s.operador.nome}</span>
                          <span>
                            Abertura: {formatCentavos(s.fundoTrocoCentavos)}
                          </span>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              {/* Detalhe da Conferência, Quebras e Sinalizações */}
              <div className="lg:col-span-7 space-y-6">
                {conferenciaDetalhe ? (
                  <div className="bg-white rounded-2xl border border-slate-200 p-6 shadow-sm space-y-5">
                    <div className="flex items-center justify-between border-b border-slate-200 pb-4">
                      <div>
                        <span className="text-xs font-bold uppercase text-amber-700">
                          Auditoria de Fechamento Cego
                        </span>
                        <h3 className="text-lg font-extrabold text-slate-900">
                          Esperado × Declarado × Diferença
                        </h3>
                      </div>
                      <span className="px-3 py-1 rounded-full bg-slate-100 text-slate-800 text-xs font-bold">
                        {conferenciaDetalhe.status}
                      </span>
                    </div>

                    {/* Composição Física da Gaveta */}
                    <div className="grid grid-cols-2 sm:grid-cols-5 gap-2.5 bg-slate-50 p-4 rounded-xl border border-slate-200 text-xs">
                      <div>
                        <span className="text-slate-500 block">Abertura</span>
                        <strong>
                          {formatCentavos(
                            conferenciaDetalhe.composicaoDinheiro.aberturaCentavos
                          )}
                        </strong>
                      </div>
                      <div>
                        <span className="text-slate-500 block">
                          Vendas Espécie
                        </span>
                        <strong className="text-emerald-700">
                          +
                          {formatCentavos(
                            conferenciaDetalhe.composicaoDinheiro.vendasCentavos
                          )}
                        </strong>
                      </div>
                      <div>
                        <span className="text-slate-500 block">Suprimentos</span>
                        <strong className="text-emerald-700">
                          +
                          {formatCentavos(
                            conferenciaDetalhe.composicaoDinheiro
                              .suprimentosCentavos
                          )}
                        </strong>
                      </div>
                      <div>
                        <span className="text-slate-500 block">Sangrias</span>
                        <strong className="text-rose-700">
                          -
                          {formatCentavos(
                            conferenciaDetalhe.composicaoDinheiro.sangriasCentavos
                          )}
                        </strong>
                      </div>
                      <div>
                        <span className="text-slate-500 block">
                          Esperado Gaveta
                        </span>
                        <strong className="text-slate-900 text-sm">
                          {formatCentavos(
                            conferenciaDetalhe.composicaoDinheiro.esperadoCentavos
                          )}
                        </strong>
                      </div>
                    </div>

                    {/* Tabela por Método */}
                    <div className="overflow-x-auto">
                      <table className="w-full text-xs text-left border-collapse">
                        <thead>
                          <tr className="border-b border-slate-200 text-slate-500 uppercase">
                            <th className="py-2">Método</th>
                            <th className="py-2">Esperado</th>
                            <th className="py-2">Declarado (Cego)</th>
                            <th className="py-2">Diferença</th>
                            <th className="py-2">Situação</th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-slate-100">
                          {conferenciaDetalhe.porMetodo.map((item) => (
                            <tr key={item.metodo}>
                              <td className="py-2.5 font-mono font-bold">
                                {item.metodo}
                              </td>
                              <td className="py-2.5">
                                {formatCentavos(item.esperadoCentavos)}
                              </td>
                              <td className="py-2.5 font-semibold">
                                {formatCentavos(item.declaradoCentavos)}
                              </td>
                              <td
                                className={`py-2.5 font-bold ${
                                  item.diferencaCentavos < 0
                                    ? 'text-rose-700'
                                    : item.diferencaCentavos > 0
                                    ? 'text-amber-700'
                                    : 'text-emerald-700'
                                }`}
                              >
                                {formatCentavos(item.diferencaCentavos)}
                              </td>
                              <td className="py-2.5">
                                <span
                                  className={`px-2 py-0.5 rounded font-bold text-[10px] ${
                                    item.situacao === 'OK'
                                      ? 'bg-emerald-100 text-emerald-800'
                                      : item.situacao === 'QUEBRA'
                                      ? 'bg-rose-100 text-rose-800'
                                      : 'bg-amber-100 text-amber-900'
                                  }`}
                                >
                                  {item.situacao}
                                </span>
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>

                    {/* Sinalizações de Auditoria (SALDO_ESPERADO_NEGATIVO) */}
                    {conferenciaDetalhe.sinalizacoes.length > 0 && (
                      <div className="rounded-xl bg-rose-50 border border-rose-200 p-4 space-y-2">
                        <div className="flex items-center gap-2 text-rose-800 font-bold text-xs">
                          <ShieldAlert className="w-4 h-4" />
                          <span>
                            Alerta de Auditoria: Sangria Acima do Saldo Esperado
                            (`SALDO_ESPERADO_NEGATIVO`)
                          </span>
                        </div>
                        {conferenciaDetalhe.sinalizacoes.map((sin) => (
                          <div
                            key={sin.id}
                            className="text-xs text-rose-900 flex justify-between"
                          >
                            <span>
                              {sin.tipo}: {sin.motivo}
                            </span>
                            <strong>{formatCentavos(sin.valorCentavos)}</strong>
                          </div>
                        ))}
                      </div>
                    )}

                    {conferenciaDetalhe.status !== 'CONFERIDA' && (
                      <div className="flex gap-2 pt-2">
                        <input
                          type="text"
                          value={obsConferencia}
                          onChange={(e) => setObsConferencia(e.target.value)}
                          placeholder="Parecer da conferência gerencial..."
                          className="flex-1 px-3 py-2 rounded-xl border border-slate-300 text-xs"
                        />
                        <button
                          type="button"
                          onClick={() =>
                            handleHomologarConferencia(
                              conferenciaDetalhe.sessaoId
                            )
                          }
                          className="px-4 py-2 rounded-xl bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-bold"
                        >
                          Homologar Conferência
                        </button>
                      </div>
                    )}
                  </div>
                ) : (
                  <div className="bg-white rounded-2xl border border-slate-200 p-6 text-xs text-slate-500">
                    Selecione uma sessão de caixa ao lado para visualizar o
                    extrato gerencial, sinalizações `SALDO_ESPERADO_NEGATIVO` e a
                    conferência do fechamento cego.
                  </div>
                )}

                {/* Extrato Completo de Movimentações da Sessão Selecionada */}
                {movimentacoesGerenciais.length > 0 && (
                  <div className="bg-white rounded-2xl border border-slate-200 p-6 shadow-sm space-y-3">
                    <h3 className="text-sm font-bold text-slate-900">
                      Extrato Imutável de Movimentações (`movimentacoes_caixa`)
                    </h3>
                    <div className="space-y-2">
                      {movimentacoesGerenciais.map((m) => (
                        <div
                          key={m.id}
                          className="p-3 rounded-xl bg-slate-50 border border-slate-200 text-xs flex items-center justify-between gap-3"
                        >
                          <div>
                            <div className="flex items-center gap-2">
                              <span className="font-mono font-bold text-slate-900">
                                {m.tipo}
                              </span>
                              <span
                                className={`px-1.5 py-0.5 rounded text-[10px] font-bold ${
                                  m.natureza === 'ENTRADA'
                                    ? 'bg-emerald-100 text-emerald-800'
                                    : 'bg-rose-100 text-rose-800'
                                }`}
                              >
                                {m.natureza}
                              </span>
                              {m.sinalizacao && (
                                <span className="px-2 py-0.5 rounded bg-rose-600 text-white text-[10px] font-bold">
                                  {m.sinalizacao}
                                </span>
                              )}
                            </div>
                            <p className="text-slate-600 mt-0.5">{m.motivo}</p>
                          </div>

                          <div className="flex items-center gap-3">
                            <strong className="text-sm text-slate-900">
                              {formatCentavos(m.valorCentavos)}
                            </strong>
                            {(m.tipo === 'SANGRIA' ||
                              m.tipo === 'SUPRIMENTO') && (
                              <button
                                type="button"
                                onClick={() => handleEstornarMovimentacao(m)}
                                className="px-2.5 py-1 rounded-lg border border-slate-300 hover:bg-rose-50 hover:border-rose-300 text-slate-700 hover:text-rose-700 font-semibold flex items-center gap-1"
                                title="Gerar lançamento reverso imutável"
                              >
                                <RotateCcw className="w-3 h-3" />
                                <span>Estornar</span>
                              </button>
                            )}
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
};
