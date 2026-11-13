import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  ChefHat,
  Coffee,
  Utensils,
  Layers,
  Wifi,
  WifiOff,
  Volume2,
  VolumeX,
  AlertOctagon,
  PlusCircle,
  RefreshCw,
  BellRing,
  CheckCircle2,
  AlertTriangle,
  Sparkles,
  Activity,
} from 'lucide-react';
import type {
  FiltroEstacaoKds,
  PedidoCozinhaDto,
  PedidoCozinhaEventoDto,
  ProblemDetail,
} from './types/kds';
import {
  fetchReplayEventosCozinha,
  fetchSnapshotCozinha,
  getSavedLastSeq,
  KdsStompConnection,
  saveLastSeq,
  simularNovoPedidoNaOutbox,
  transicionarPedidoKds,
} from './lib/kdsApi';
import {
  playNewOrderBell,
  playOrderReadyChime,
  playRemakeAlertBeep,
} from './lib/soundAlert';
import { KdsOrderCard } from './components/KdsOrderCard';
import { DrawerRuptura86 } from './components/DrawerRuptura86';

type FiltroStatusTab = 'TODOS' | 'CONFIRMADO' | 'EM_PREPARO' | 'PRONTO';

export const App: React.FC = () => {
  const [estacao, setEstacao] = useState<FiltroEstacaoKds>('TODAS');
  const [statusTab, setStatusTab] = useState<FiltroStatusTab>('TODOS');
  const [pedidos, setPedidos] = useState<PedidoCozinhaDto[]>([]);
  const [seqAtual, setSeqAtual] = useState<number>(() => getSavedLastSeq());
  const [wsConnected, setWsConnected] = useState<boolean>(true);
  const [soundEnabled, setSoundEnabled] = useState<boolean>(true);
  const [loadingPedidoId, setLoadingPedidoId] = useState<string | null>(null);
  const [nowMs, setNowMs] = useState<number>(() => Date.now());
  const [drawer86Open, setDrawer86Open] = useState<boolean>(false);

  // Banner de notificações operacionais (ex.: Balcão chamado, Replay Monotônico executado, Conflito 409)
  const [toastBanner, setToastBanner] = useState<{
    tipo: 'SUCCESS' | 'INFO' | 'WARN' | 'ERROR';
    mensagem: string;
  } | null>(null);

  const seqRef = useRef<number>(seqAtual);
  seqRef.current = seqAtual;

  const soundRef = useRef<boolean>(soundEnabled);
  soundRef.current = soundEnabled;

  const stompRef = useRef<KdsStompConnection | null>(null);

  const mostrarNotificacao = useCallback(
    (
      tipo: 'SUCCESS' | 'INFO' | 'WARN' | 'ERROR',
      mensagem: string,
      duracaoMs = 5000
    ) => {
      setToastBanner({ tipo, mensagem });
      window.setTimeout(() => {
        setToastBanner((atual) =>
          atual?.mensagem === mensagem ? null : atual
        );
      }, duracaoMs);
    },
    []
  );

  // Relógio de segundo em segundo para atualizar cronômetros e SLA dos cards
  useEffect(() => {
    const timer = window.setInterval(() => {
      setNowMs(Date.now());
    }, 1000);
    return () => window.clearInterval(timer);
  }, []);

  // Aplica um evento individual da fila/outbox no estado local de forma idempotente e monotônica
  const aplicarEventoMonotonico = useCallback(
    (ev: PedidoCozinhaEventoDto, origemReplay = false) => {
      setSeqAtual((prevSeq) => {
        const next = Math.max(prevSeq, ev.seq);
        saveLastSeq(next);
        return next;
      });

      setPedidos((prevPedidos) => {
        // Se foi entregue ou cancelado, remove do painel ativo da cozinha
        if (
          ev.status === 'ENTREGUE' ||
          ev.status === 'CANCELADO' ||
          ev.status === 'ESTORNADO'
        ) {
          return prevPedidos.filter((p) => p.pedidoId !== ev.pedidoId);
        }

        if (!ev.pedido) {
          return prevPedidos;
        }

        // Filtra itens pela estação ativa se não for TODAS
        const itensDaEstacao =
          estacao === 'TODAS'
            ? ev.pedido.itens
            : ev.pedido.itens.filter((i) => i.estacao === estacao);

        if (itensDaEstacao.length === 0) {
          return prevPedidos.filter((p) => p.pedidoId !== ev.pedidoId);
        }

        const pedidoFiltrado: PedidoCozinhaDto = {
          ...ev.pedido,
          itens: itensDaEstacao,
        };

        const existente = prevPedidos.find((p) => p.pedidoId === ev.pedidoId);
        if (existente && existente.versao > pedidoFiltrado.versao) {
          // Proteção contra evento atrasado/fora de ordem
          return prevPedidos;
        }

        if (!existente) {
          return [...prevPedidos, pedidoFiltrado];
        }

        return prevPedidos.map((p) =>
          p.pedidoId === pedidoFiltrado.pedidoId ? pedidoFiltrado : p
        );
      });

      if (!origemReplay) {
        if (ev.tipo === 'PEDIDO_CONFIRMADO') {
          playNewOrderBell(soundRef.current);
          mostrarNotificacao(
            'INFO',
            `Novo Pedido #${ev.pedido?.numeroDiario ?? ''} entrou na fila de produção (Seq #${ev.seq}).`
          );
        } else if (ev.tipo === 'PEDIDO_PRONTO') {
          playOrderReadyChime(soundRef.current);
          mostrarNotificacao(
            'SUCCESS',
            `Pedido #${ev.pedido?.numeroDiario ?? ''} marcado como PRONTO! Notificação enviada ao monitor de balcão.`
          );
        } else if (ev.tipo === 'PEDIDO_REFEITO') {
          playRemakeAlertBeep(soundRef.current);
          mostrarNotificacao(
            'WARN',
            `Atenção: Pedido #${ev.pedido?.numeroDiario ?? ''} retornou para REFAZER (${ev.motivo || 'Ajuste solicitado'}).`
          );
        }
      }
    },
    [estacao, mostrarNotificacao]
  );

  // Carrega snapshot inicial da cozinha + conecta STOMP
  const sincronizarSnapshotInicial = useCallback(async () => {
    const snap = await fetchSnapshotCozinha(estacao);
    setPedidos(snap.pedidos);
    setSeqAtual(snap.seqAtual);
    saveLastSeq(snap.seqAtual);
  }, [estacao]);

  // Executa Replay Monotônico (`GET /api/v1/cozinha/eventos?desdeSeq=...`)
  const executarReplayDesdeSeq = useCallback(
    async (seqBase: number) => {
      const eventosPerdidos = await fetchReplayEventosCozinha(seqBase, estacao);
      if (eventosPerdidos.length > 0) {
        for (const ev of eventosPerdidos) {
          aplicarEventoMonotonico(ev, true);
        }
        playNewOrderBell(soundRef.current);
        mostrarNotificacao(
          'SUCCESS',
          `Replay Monotônico concluído (desdeSeq=${seqBase}): ${eventosPerdidos.length} evento(s) recuperado(s) sem perda!`
        );
      } else {
        mostrarNotificacao(
          'INFO',
          `Replay verificado (desdeSeq=${seqBase}): fila já está 100% sincronizada (Seq #${seqRef.current}).`
        );
      }
    },
    [aplicarEventoMonotonico, estacao, mostrarNotificacao]
  );

  useEffect(() => {
    sincronizarSnapshotInicial();

    const conn = new KdsStompConnection();
    stompRef.current = conn;

    conn.start(
      estacao,
      (ev) => {
        aplicarEventoMonotonico(ev, false);
      },
      (connected) => {
        setWsConnected(connected);
      }
    );

    return () => {
      conn.stop();
    };
  }, [
    estacao,
    sincronizarSnapshotInicial,
    aplicarEventoMonotonico,
    executarReplayDesdeSeq,
  ]);

  // Handlers de transição com tratamento de Optimistic Locking (409 TRANSICAO_CONCORRENTE)
  const executarTransicao = async (
    pedido: PedidoCozinhaDto,
    acao: 'iniciar-preparo' | 'marcar-pronto' | 'refazer' | 'entregar',
    motivo?: string
  ) => {
    setLoadingPedidoId(pedido.pedidoId);
    try {
      await transicionarPedidoKds(pedido.pedidoId, acao, pedido.versao, motivo);
    } catch (err: unknown) {
      const prob = err as ProblemDetail;
      if (prob?.code === 'TRANSICAO_CONCORRENTE') {
        mostrarNotificacao(
          'ERROR',
          `Conflito de concorrência (409): ${prob.detail} Sincronizando estado atualizado...`
        );
        await sincronizarSnapshotInicial();
      } else {
        mostrarNotificacao(
          'ERROR',
          prob?.detail || 'Não foi possível concluir a transição do pedido.'
        );
      }
    } finally {
      setLoadingPedidoId(null);
    }
  };

  // Simulador didático de queda de rede + chegada de pedido offline + replay via desdeSeq
  const handleSimularQuedaEReplay = async () => {
    const seqAntesDaQueda = seqRef.current;
    setWsConnected(false);
    // Cria um pedido na outbox SEM publicar no WebSocket (simulando pacote perdido durante queda de Wi-Fi)
    const evPerdido = simularNovoPedidoNaOutbox(false);
    mostrarNotificacao(
      'WARN',
      `Simulando oscilação de rede: Pedido #${evPerdido.pedido?.numeroDiario} foi gravado no servidor (Seq #${evPerdido.seq}) enquanto o KDS estava desconectado...`
    );

    window.setTimeout(async () => {
      setWsConnected(true);
      await executarReplayDesdeSeq(seqAntesDaQueda);
    }, 900);
  };

  // Ordenação por prioridade operacional (mais antigos no topo; PRONTO agrupado à direita/final)
  const pedidosFiltrados = useMemo(() => {
    const filtrados =
      statusTab === 'TODOS'
        ? pedidos
        : pedidos.filter((p) => p.status === statusTab);

    return [...filtrados].sort((a, b) => {
      if (a.status === 'PRONTO' && b.status !== 'PRONTO') return 1;
      if (a.status !== 'PRONTO' && b.status === 'PRONTO') return -1;
      return (
        new Date(a.confirmadoEm).getTime() - new Date(b.confirmadoEm).getTime()
      );
    });
  }, [pedidos, statusTab]);

  // Contadores em tempo real para o cabeçalho
  const contadores = useMemo(() => {
    const confirmados = pedidos.filter((p) => p.status === 'CONFIRMADO').length;
    const emPreparo = pedidos.filter((p) => p.status === 'EM_PREPARO').length;
    const prontos = pedidos.filter((p) => p.status === 'PRONTO').length;
    const atrasadosCriticos = pedidos.filter((p) => {
      if (p.status === 'PRONTO') return false;
      const mins = Math.floor(
        (nowMs - new Date(p.confirmadoEm).getTime()) / 60000
      );
      return mins >= 10;
    }).length;

    return {
      total: pedidos.length,
      confirmados,
      emPreparo,
      prontos,
      atrasadosCriticos,
    };
  }, [pedidos, nowMs]);

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col select-none">
      {/* Topbar de Alta Densidade do KDS */}
      <header className="sticky top-0 z-30 bg-slate-900/95 backdrop-blur-md border-b border-slate-800 px-5 py-3.5">
        <div className="flex flex-wrap items-center justify-between gap-4">
          {/* Identidade + Status STOMP + Seq Monotônico */}
          <div className="flex items-center gap-3.5">
            <div className="w-11 h-11 rounded-2xl bg-gradient-to-br from-amber-500 to-orange-600 flex items-center justify-center text-slate-950 shadow-lg shadow-amber-500/20">
              <ChefHat className="w-6 h-6 stroke-[2.5]" />
            </div>
            <div>
              <div className="flex items-center gap-2.5">
                <h1 className="text-xl font-black tracking-tight text-white">
                  ORDERFLOW <span className="text-amber-400">KDS</span>
                </h1>
                <span
                  className={`inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-bold border ${
                    wsConnected
                      ? 'bg-emerald-500/15 text-emerald-300 border-emerald-500/30'
                      : 'bg-rose-500/20 text-rose-300 border-rose-500/40 animate-pulse'
                  }`}
                >
                  {wsConnected ? (
                    <>
                      <Wifi className="w-3.5 h-3.5" />
                      <span>STOMP ATIVO</span>
                    </>
                  ) : (
                    <>
                      <WifiOff className="w-3.5 h-3.5" />
                      <span>RECONECTANDO...</span>
                    </>
                  )}
                </span>
                <span
                  className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-mono font-bold bg-slate-800 text-sky-300 border border-slate-700"
                  title="Último número de sequência monotônico processado pelo KDS"
                >
                  <Activity className="w-3.5 h-3.5 text-sky-400" />
                  Seq #{seqAtual}
                </span>
              </div>
              <p className="text-xs text-slate-400 mt-0.5">
                Painel de Produção em Tempo Real · SLA: &lt;5m Verde · 5-10m
                Âmbar · &gt;10m Crítico
              </p>
            </div>
          </div>

          {/* Seletor de Estação de Preparo (TODAS | BAR | COZINHA) */}
          <div className="flex items-center bg-slate-950 p-1.5 rounded-2xl border border-slate-800">
            <button
              type="button"
              onClick={() => setEstacao('TODAS')}
              className={`px-4 py-2 rounded-xl text-xs font-black uppercase tracking-wider flex items-center gap-2 transition cursor-pointer ${
                estacao === 'TODAS'
                  ? 'bg-amber-500 text-slate-950 shadow-md'
                  : 'text-slate-400 hover:text-white'
              }`}
            >
              <Layers className="w-4 h-4" />
              <span>Todas Estações</span>
            </button>
            <button
              type="button"
              onClick={() => setEstacao('BAR')}
              className={`px-4 py-2 rounded-xl text-xs font-black uppercase tracking-wider flex items-center gap-2 transition cursor-pointer ${
                estacao === 'BAR'
                  ? 'bg-cyan-500 text-slate-950 shadow-md'
                  : 'text-slate-400 hover:text-white'
              }`}
            >
              <Coffee className="w-4 h-4" />
              <span>Bar / Barista</span>
            </button>
            <button
              type="button"
              onClick={() => setEstacao('COZINHA')}
              className={`px-4 py-2 rounded-xl text-xs font-black uppercase tracking-wider flex items-center gap-2 transition cursor-pointer ${
                estacao === 'COZINHA'
                  ? 'bg-orange-500 text-slate-950 shadow-md'
                  : 'text-slate-400 hover:text-white'
              }`}
            >
              <Utensils className="w-4 h-4" />
              <span>Cozinha / Forno</span>
            </button>
          </div>

          {/* Ações Rápidas Operacionais & Testes de Resiliência */}
          <div className="flex items-center gap-2.5">
            <button
              type="button"
              onClick={() => {
                simularNovoPedidoNaOutbox(true);
              }}
              className="px-3.5 py-2.5 rounded-xl bg-slate-800 hover:bg-slate-700 border border-slate-700 text-xs font-bold text-slate-200 flex items-center gap-1.5 transition cursor-pointer"
              title="Injeta um novo pedido na fila em tempo real"
            >
              <PlusCircle className="w-4 h-4 text-emerald-400" />
              <span>+ Pedido Teste</span>
            </button>

            <button
              type="button"
              onClick={handleSimularQuedaEReplay}
              className="px-3.5 py-2.5 rounded-xl bg-sky-500/15 hover:bg-sky-500/25 border border-sky-500/40 text-xs font-bold text-sky-300 flex items-center gap-1.5 transition cursor-pointer"
              title="Simula queda de conexão, cria pedido perdido e recupera via GET /api/v1/cozinha/eventos?desdeSeq=..."
            >
              <RefreshCw className="w-4 h-4 text-sky-400" />
              <span>Testar Replay (desdeSeq)</span>
            </button>

            <button
              type="button"
              onClick={() => setDrawer86Open(true)}
              className="px-4 py-2.5 rounded-xl bg-rose-600 hover:bg-rose-500 text-white font-black text-xs uppercase tracking-wider flex items-center gap-2 shadow-lg shadow-rose-600/25 transition cursor-pointer"
            >
              <AlertOctagon className="w-4 h-4" />
              <span>Ruptura 86</span>
            </button>

            <button
              type="button"
              onClick={() => setSoundEnabled((prev) => !prev)}
              className={`p-2.5 rounded-xl border transition cursor-pointer ${
                soundEnabled
                  ? 'bg-slate-800 border-slate-700 text-amber-400'
                  : 'bg-slate-900 border-slate-800 text-slate-500'
              }`}
              title={
                soundEnabled ? 'Alertas sonoros ativados' : 'Som silenciado'
              }
            >
              {soundEnabled ? (
                <Volume2 className="w-5 h-5" />
              ) : (
                <VolumeX className="w-5 h-5" />
              )}
            </button>
          </div>
        </div>

        {/* Sub-barra de Filtros de Status e Indicadores SLA */}
        <div className="mt-3 pt-3 border-t border-slate-800/80 flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => setStatusTab('TODOS')}
              className={`px-3.5 py-1.5 rounded-xl text-xs font-bold transition cursor-pointer flex items-center gap-2 ${
                statusTab === 'TODOS'
                  ? 'bg-white text-slate-950 font-black'
                  : 'bg-slate-800/80 text-slate-300 hover:bg-slate-800'
              }`}
            >
              <span>Todos Ativos</span>
              <span className="px-1.5 py-0.5 rounded-md bg-slate-900/20 text-[11px] font-mono">
                {contadores.total}
              </span>
            </button>

            <button
              type="button"
              onClick={() => setStatusTab('CONFIRMADO')}
              className={`px-3.5 py-1.5 rounded-xl text-xs font-bold transition cursor-pointer flex items-center gap-2 ${
                statusTab === 'CONFIRMADO'
                  ? 'bg-sky-500 text-slate-950 font-black'
                  : 'bg-slate-800/80 text-sky-300 hover:bg-slate-800'
              }`}
            >
              <span>Aguardando Início</span>
              <span className="px-1.5 py-0.5 rounded-md bg-slate-950/30 text-[11px] font-mono">
                {contadores.confirmados}
              </span>
            </button>

            <button
              type="button"
              onClick={() => setStatusTab('EM_PREPARO')}
              className={`px-3.5 py-1.5 rounded-xl text-xs font-bold transition cursor-pointer flex items-center gap-2 ${
                statusTab === 'EM_PREPARO'
                  ? 'bg-amber-500 text-slate-950 font-black'
                  : 'bg-slate-800/80 text-amber-300 hover:bg-slate-800'
              }`}
            >
              <span>Em Preparo</span>
              <span className="px-1.5 py-0.5 rounded-md bg-slate-950/30 text-[11px] font-mono">
                {contadores.emPreparo}
              </span>
            </button>

            <button
              type="button"
              onClick={() => setStatusTab('PRONTO')}
              className={`px-3.5 py-1.5 rounded-xl text-xs font-bold transition cursor-pointer flex items-center gap-2 ${
                statusTab === 'PRONTO'
                  ? 'bg-emerald-500 text-slate-950 font-black'
                  : 'bg-slate-800/80 text-emerald-300 hover:bg-slate-800'
              }`}
            >
              <span>Prontos p/ Retirada</span>
              <span className="px-1.5 py-0.5 rounded-md bg-slate-950/30 text-[11px] font-mono">
                {contadores.prontos}
              </span>
            </button>
          </div>

          {/* Resumo de SLA Crítico */}
          <div className="flex items-center gap-4 text-xs">
            {contadores.atrasadosCriticos > 0 ? (
              <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-xl bg-rose-500/20 border border-rose-500/40 text-rose-300 font-bold animate-pulse">
                <AlertTriangle className="w-4 h-4 text-rose-400" />
                <span>
                  {contadores.atrasadosCriticos} pedido(s) acima de 10 min (SLA
                  Crítico)
                </span>
              </div>
            ) : (
              <div className="inline-flex items-center gap-1.5 text-emerald-400 font-semibold">
                <Sparkles className="w-4 h-4" />
                <span>Todos os pedidos dentro da meta operacional</span>
              </div>
            )}
          </div>
        </div>
      </header>

      {/* Banner de Notificação em Tempo Real */}
      {toastBanner && (
        <div
          className={`mx-5 mt-4 px-4 py-3 rounded-2xl border flex items-center justify-between gap-3 shadow-lg ${
            toastBanner.tipo === 'SUCCESS'
              ? 'bg-emerald-950/90 border-emerald-500/60 text-emerald-200'
              : toastBanner.tipo === 'WARN'
              ? 'bg-amber-950/90 border-amber-500/60 text-amber-200'
              : toastBanner.tipo === 'ERROR'
              ? 'bg-rose-950/90 border-rose-500/60 text-rose-200'
              : 'bg-sky-950/90 border-sky-500/60 text-sky-200'
          }`}
        >
          <div className="flex items-center gap-2.5 text-sm font-bold">
            <BellRing className="w-5 h-5 shrink-0" />
            <span>{toastBanner.mensagem}</span>
          </div>
          <button
            type="button"
            onClick={() => setToastBanner(null)}
            className="text-xs font-bold underline opacity-80 hover:opacity-100 cursor-pointer"
          >
            Fechar
          </button>
        </div>
      )}

      {/* Grid Principal de Comandas do KDS */}
      <main className="flex-1 p-5">
        {pedidosFiltrados.length === 0 ? (
          <div className="h-[65vh] rounded-3xl border-2 border-dashed border-slate-800 flex flex-col items-center justify-center text-center p-8">
            <div className="w-16 h-16 rounded-2xl bg-slate-900 border border-slate-800 flex items-center justify-center text-emerald-400 mb-4">
              <CheckCircle2 className="w-9 h-9" />
            </div>
            <h2 className="text-xl font-black text-white">
              Fila Limpa na Estação ({estacao})
            </h2>
            <p className="text-sm text-slate-400 max-w-md mt-1">
              Nenhum pedido pendente com o filtro selecionado. Assim que o PDV
              confirmar uma venda, o card entrará automaticamente com alerta
              sonoro.
            </p>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4 items-start">
            {pedidosFiltrados.map((pedido) => (
              <KdsOrderCard
                key={pedido.pedidoId}
                pedido={pedido}
                nowMs={nowMs}
                loadingPedidoId={loadingPedidoId}
                onIniciarPreparo={(p) =>
                  executarTransicao(p, 'iniciar-preparo')
                }
                onMarcarPronto={(p) => executarTransicao(p, 'marcar-pronto')}
                onRefazer={(p, motivo) =>
                  executarTransicao(p, 'refazer', motivo)
                }
                onEntregar={(p) => executarTransicao(p, 'entregar')}
              />
            ))}
          </div>
        )}
      </main>

      {/* Drawer Rápido de Ruptura 86 (Insumos) */}
      <DrawerRuptura86
        isOpen={drawer86Open}
        onClose={() => setDrawer86Open(false)}
        onInsumoUpdated={(msg) => mostrarNotificacao('WARN', msg)}
      />
    </div>
  );
};

export default App;
