// ═══════════════════════════════════════════════════════════════════════════
// Cliente HTTP + Motor de Sincronização Monotônica (desdeSeq) + Simulação KDS
// Suporta operação conectada ao Backend Spring Boot (:8080) e modo local
// integrado via BroadcastChannel com o Frontend Master.
// ═══════════════════════════════════════════════════════════════════════════

import axios, { type InternalAxiosRequestConfig } from 'axios';
import { Client, type IMessage } from '@stomp/stompjs';
import type {
  CozinhaSnapshotResponse,
  EstacaoPreparo,
  FiltroEstacaoKds,
  Insumo,
  InsumoDisponibilidadeResponse,
  PedidoCozinhaDto,
  PedidoCozinhaEventoDto,
  PedidoProntoEventoDto,
  ProblemDetail,
} from '../types/kds';

export const LOJA_MATRIZ_ID = '019325f0-8a00-7000-8000-000000000001';
const KDS_SIM_STORE_KEY = 'orderflow:kds_sim_store_v1';
const KDS_SIM_MODE_KEY = 'orderflow:kds_simulation_mode';
const KDS_SEQ_KEY = 'orderflow:kds_last_seq';

export const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1';

export function isKdsSimulationMode(): boolean {
  const val = localStorage.getItem(KDS_SIM_MODE_KEY);
  return val === null ? false : val === 'true';
}

export function setKdsSimulationMode(enabled: boolean): void {
  localStorage.setItem(KDS_SIM_MODE_KEY, String(enabled));
}

export function getSavedLastSeq(): number {
  const raw = localStorage.getItem(KDS_SEQ_KEY);
  return raw ? Number(raw) || 0 : 0;
}

export function saveLastSeq(seq: number): void {
  localStorage.setItem(KDS_SEQ_KEY, String(seq));
}

export function generateUuidV7(): string {
  const now = BigInt(Date.now());
  const bytes = new Uint8Array(16);
  if (typeof crypto !== 'undefined' && crypto.getRandomValues) {
    crypto.getRandomValues(bytes);
  } else {
    for (let i = 0; i < 16; i += 1) {
      bytes[i] = Math.floor(Math.random() * 256);
    }
  }
  bytes[0] = Number((now >> 40n) & 0xffn);
  bytes[1] = Number((now >> 32n) & 0xffn);
  bytes[2] = Number((now >> 24n) & 0xffn);
  bytes[3] = Number((now >> 16n) & 0xffn);
  bytes[4] = Number((now >> 8n) & 0xffn);
  bytes[5] = Number(now & 0xffn);
  bytes[6] = (bytes[6] & 0x0f) | 0x70;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20, 32)}`;
}

// ─────────────────────────── Barramento STOMP + BroadcastChannel ───────────────────────────

const localBus = new EventTarget();
const broadcastChannel =
  typeof BroadcastChannel !== 'undefined'
    ? new BroadcastChannel('orderflow_stomp_bus')
    : null;

if (broadcastChannel) {
  broadcastChannel.onmessage = (ev) => {
    const { destination, payload } = ev.data || {};
    if (destination) {
      localBus.dispatchEvent(
        new CustomEvent(destination, { detail: payload })
      );
    }
  };
}

export function publishStompEvent(destination: string, payload: unknown): void {
  localBus.dispatchEvent(new CustomEvent(destination, { detail: payload }));
  broadcastChannel?.postMessage({ destination, payload });
}

// ─────────────────────────── Estado Inicial Realista da Cozinha ───────────────────────────

interface KdsSimulationStore {
  seqAtual: number;
  pedidos: PedidoCozinhaDto[];
  eventosOutbox: PedidoCozinhaEventoDto[];
  insumos: Insumo[];
}

function createInitialOrders(): PedidoCozinhaDto[] {
  const now = Date.now();
  return [
    {
      pedidoId: '019325f0-8a00-7000-8020-000000000101',
      numeroDiario: 101,
      identificadorCliente: 'Ana Beatriz',
      mesa: '04',
      tipoAtendimento: 'MESA',
      status: 'CONFIRMADO',
      versao: 1,
      observacao: 'Servir os cafés juntos com os croissants quentes',
      confirmadoEm: new Date(now - 2 * 60 * 1000).toISOString(), // 2 min atrás (SLA OK)
      preparoIniciadoEm: null,
      prontoEm: null,
      itens: [
        {
          itemId: '019325f0-8a00-7000-8021-000000000001',
          produtoNome: 'Cappuccino Italiano Clássico',
          quantidade: 2,
          estacao: 'BAR',
          modificadores: [
            'Leite de Aveia Barista',
            '2x Shot Extra Espresso Arábica',
          ],
          observacao: 'Bem quente · Sem cacau em 1 unidade',
          comboNome: null,
        },
        {
          itemId: '019325f0-8a00-7000-8021-000000000002',
          produtoNome: 'Croissant Francês na Manteiga',
          quantidade: 2,
          estacao: 'COZINHA',
          modificadores: ['Aquecido no forno de lastro'],
          observacao: null,
          comboNome: null,
        },
      ],
    },
    {
      pedidoId: '019325f0-8a00-7000-8020-000000000102',
      numeroDiario: 102,
      identificadorCliente: 'Carlos Eduardo',
      mesa: null,
      tipoAtendimento: 'BALCAO',
      status: 'EM_PREPARO',
      versao: 2,
      observacao: 'Para viagem (sacola reforçada)',
      confirmadoEm: new Date(now - 7 * 60 * 1000).toISOString(), // 7 min atrás (SLA Atenção)
      preparoIniciadoEm: new Date(now - 5 * 60 * 1000).toISOString(),
      prontoEm: null,
      itens: [
        {
          itemId: '019325f0-8a00-7000-8021-000000000003',
          produtoNome: 'Oat Milk Latte Especial',
          quantidade: 1,
          estacao: 'BAR',
          modificadores: ['Copo Grande 400ml', 'Toque de Caramelo Salgado'],
          observacao: 'Pouca espuma',
          comboNome: 'Combo Manhã Parisiense',
        },
        {
          itemId: '019325f0-8a00-7000-8021-000000000004',
          produtoNome: 'Porção Pão de Queijo Canastra (6 un)',
          quantidade: 1,
          estacao: 'COZINHA',
          modificadores: [],
          observacao: null,
          comboNome: 'Combo Manhã Parisiense',
        },
      ],
    },
    {
      pedidoId: '019325f0-8a00-7000-8020-000000000103',
      numeroDiario: 103,
      identificadorCliente: 'Fernanda Lima',
      mesa: '12',
      tipoAtendimento: 'MESA',
      status: 'EM_PREPARO',
      versao: 2,
      observacao: 'Cliente com pressa — prioridade',
      confirmadoEm: new Date(now - 12 * 60 * 1000).toISOString(), // 12 min atrás (SLA Crítico > 10 min)
      preparoIniciadoEm: new Date(now - 9 * 60 * 1000).toISOString(),
      prontoEm: null,
      itens: [
        {
          itemId: '019325f0-8a00-7000-8021-000000000005',
          produtoNome: 'Toast Rústico de Avocado & Ovos Pochê',
          quantidade: 2,
          estacao: 'COZINHA',
          modificadores: ['Pão Levain Tostado', 'Sem pimenta calabresa'],
          observacao: 'Gema bem mole',
          comboNome: null,
        },
      ],
    },
    {
      pedidoId: '019325f0-8a00-7000-8020-000000000104',
      numeroDiario: 104,
      identificadorCliente: 'Gabriel Santos',
      mesa: null,
      tipoAtendimento: 'BALCAO',
      status: 'PRONTO',
      versao: 3,
      observacao: null,
      confirmadoEm: new Date(now - 6 * 60 * 1000).toISOString(),
      preparoIniciadoEm: new Date(now - 4 * 60 * 1000).toISOString(),
      prontoEm: new Date(now - 1 * 60 * 1000).toISOString(),
      itens: [
        {
          itemId: '019325f0-8a00-7000-8021-000000000006',
          produtoNome: 'Cold Brew Tonic Citrus',
          quantidade: 1,
          estacao: 'BAR',
          modificadores: ['Gelo Cristal', 'Zest de Laranja Bahia'],
          observacao: null,
          comboNome: null,
        },
      ],
    },
  ];
}

const initialInsumosKds: Insumo[] = [
  {
    id: '019325f0-8a00-7000-8001-000000000001',
    nome: 'Grão Espresso Arábica Especial',
    unidade: 'G',
    unidadeMedida: 'G',
    quantidadeAtual: 8500,
    estoqueMinimo: 1500,
    disponivel: true,
    motivoIndisponibilidade: null,
    versao: 1,
  },
  {
    id: '019325f0-8a00-7000-8001-000000000002',
    nome: 'Leite Vegetal de Aveia Barista',
    unidade: 'ML',
    unidadeMedida: 'ML',
    quantidadeAtual: 4000,
    estoqueMinimo: 1000,
    disponivel: true,
    motivoIndisponibilidade: null,
    versao: 1,
  },
  {
    id: '019325f0-8a00-7000-8001-000000000003',
    nome: 'Massa Folhada Artesanal Francesa',
    unidade: 'UN',
    unidadeMedida: 'UN',
    quantidadeAtual: 24,
    estoqueMinimo: 8,
    disponivel: true,
    motivoIndisponibilidade: null,
    versao: 1,
  },
  {
    id: '019325f0-8a00-7000-8001-000000000004',
    nome: 'Calda de Caramelo Salgado',
    unidade: 'ML',
    unidadeMedida: 'ML',
    quantidadeAtual: 1200,
    estoqueMinimo: 300,
    disponivel: true,
    motivoIndisponibilidade: null,
    versao: 1,
  },
];

function loadKdsStore(): KdsSimulationStore {
  try {
    const raw = localStorage.getItem(KDS_SIM_STORE_KEY);
    if (raw) {
      return JSON.parse(raw) as KdsSimulationStore;
    }
  } catch {
    // fallback
  }
  const pedidos = createInitialOrders();
  const initial: KdsSimulationStore = {
    seqAtual: 10,
    pedidos,
    eventosOutbox: pedidos.map((p, idx) => ({
      seq: idx + 1,
      tipo: 'PEDIDO_CONFIRMADO',
      pedidoId: p.pedidoId,
      versao: p.versao,
      status: p.status,
      ocorridoEm: p.confirmadoEm,
      pedido: p,
    })),
    insumos: initialInsumosKds,
  };
  saveKdsStore(initial);
  return initial;
}

function saveKdsStore(store: KdsSimulationStore): void {
  localStorage.setItem(KDS_SIM_STORE_KEY, JSON.stringify(store));
}

function filtrarPedidoPorEstacao(
  pedido: PedidoCozinhaDto,
  estacao?: FiltroEstacaoKds
): PedidoCozinhaDto {
  if (!estacao || estacao === 'TODAS') {
    return pedido;
  }
  return {
    ...pedido,
    itens: pedido.itens.filter((i) => i.estacao === estacao),
  };
}

// ─────────────────────────── HTTP Client & Handlers ───────────────────────────

interface PedidoApiBackendDto {
  id: number;
  numeroMesa: number;
  status: 'RECEBIDO' | 'EM_PREPARO' | 'PRONTO' | 'ENTREGUE' | 'CANCELADO';
  versao?: number | null;
  dataHora?: string | null;
  itens?: Array<{
    id?: number;
    produtoId?: number;
    nomeProduto: string;
    quantidade: number;
    tamanhoEscolhido?: string | null;
    tipoGraoEscolhido?: string | null;
    adicionaisEscolhidos?: string | null;
    pagadorItem?: string | null;
    observacoes?: string | null;
  }>;
}

function mapPedidoBackendToKds(raw: PedidoApiBackendDto): PedidoCozinhaDto {
  const itens = (raw.itens || []).map((item, idx) => {
    const mods: string[] = [];
    if (item.tamanhoEscolhido) mods.push(`Tam: ${item.tamanhoEscolhido}`);
    if (item.tipoGraoEscolhido) mods.push(`Grão: ${item.tipoGraoEscolhido}`);
    if (item.adicionaisEscolhidos) {
      item.adicionaisEscolhidos
        .split(',')
        .map((s) => s.trim())
        .filter(Boolean)
        .forEach((a) => mods.push(a));
    }

    const isBebida =
      Boolean(item.tipoGraoEscolhido) ||
      /caf[eé]|cappuccino|espresso|latte|mocha|suco|refri|ch[aá]|bebida|frapp/i.test(
        item.nomeProduto
      );

    return {
      itemId: String(item.id ?? `${raw.id}-${idx}`),
      produtoNome: item.nomeProduto,
      quantidade: item.quantidade,
      estacao: (isBebida ? 'BAR' : 'COZINHA') as EstacaoPreparo,
      modificadores: mods,
      observacao: item.observacoes || null,
      comboNome: item.pagadorItem ? `Pagador: ${item.pagadorItem}` : null,
    };
  });

  return {
    pedidoId: String(raw.id),
    numeroDiario: raw.id,
    identificadorCliente: `Mesa ${raw.numeroMesa}`,
    mesa: String(raw.numeroMesa),
    tipoAtendimento: 'MESA',
    status: raw.status,
    versao: raw.versao ?? 0,
    observacao: null,
    confirmadoEm: raw.dataHora || new Date().toISOString(),
    preparoIniciadoEm: null,
    prontoEm: null,
    itens,
  };
}

const httpClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
    Accept: 'application/json, application/problem+json',
  },
});

httpClient.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = localStorage.getItem('orderflow:access_token');
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`);
  }
  return config;
});

export async function fetchSnapshotCozinha(
  estacao: FiltroEstacaoKds
): Promise<CozinhaSnapshotResponse> {
  if (!isKdsSimulationMode()) {
    try {
      const res = await httpClient.get<PedidoApiBackendDto[]>('/pedidos');
      if (Array.isArray(res.data)) {
        const mapeados = res.data
          .map(mapPedidoBackendToKds)
          .filter(
            (p) =>
              p.status === 'RECEBIDO' ||
              p.status === 'CONFIRMADO' ||
              p.status === 'EM_PREPARO' ||
              p.status === 'PRONTO'
          )
          .map((p) => filtrarPedidoPorEstacao(p, estacao))
          .filter((p) => p.itens.length > 0);

        const seq = res.data.reduce(
          (acc, p) => acc + p.id + (p.versao ?? 0),
          0
        );
        saveLastSeq(seq);
        return {
          seqAtual: seq,
          geradoEm: new Date().toISOString(),
          pedidos: mapeados,
        };
      }
    } catch {
      // Fallback para store se backend estiver offline no momento
    }
  }

  const store = loadKdsStore();

  const ativos = store.pedidos
    .filter(
      (p) =>
        p.status === 'RECEBIDO' ||
        p.status === 'CONFIRMADO' ||
        p.status === 'EM_PREPARO' ||
        p.status === 'PRONTO'
    )
    .map((p) => filtrarPedidoPorEstacao(p, estacao))
    .filter((p) => p.itens.length > 0);

  saveLastSeq(store.seqAtual);
  return {
    seqAtual: store.seqAtual,
    geradoEm: new Date().toISOString(),
    pedidos: ativos,
  };
}

export async function fetchReplayEventosCozinha(
  desdeSeq: number,
  estacao: FiltroEstacaoKds
): Promise<PedidoCozinhaEventoDto[]> {
  const store = loadKdsStore();

  try {
    const res = await httpClient.get<PedidoCozinhaEventoDto[]>(
      '/bridge/kds/events',
      { params: { desdeSeq: 0, estacao: 'TODAS' } }
    );
    if (Array.isArray(res.data)) {
      let changed = false;
      for (const ev of res.data) {
        if (!store.eventosOutbox.some((e) => e.seq === ev.seq && e.pedidoId === ev.pedidoId)) {
          store.eventosOutbox.push(ev);
          changed = true;
        }
        if (ev.pedido) {
          const idx = store.pedidos.findIndex((p) => p.pedidoId === ev.pedidoId);
          if (idx === -1) {
            store.pedidos.push(ev.pedido);
            changed = true;
          } else if (store.pedidos[idx].versao <= ev.pedido.versao) {
            store.pedidos[idx] = ev.pedido;
            changed = true;
          }
        }
        if (ev.seq > store.seqAtual) {
          store.seqAtual = ev.seq;
          changed = true;
        }
      }
      if (changed) {
        saveKdsStore(store);
      }
    }
  } catch {
    // fallback local
  }

  return store.eventosOutbox
    .filter((ev) => ev.seq > desdeSeq)
    .map((ev) => ({
      ...ev,
      pedido: ev.pedido ? filtrarPedidoPorEstacao(ev.pedido, estacao) : null,
    }));
}

export async function transicionarPedidoKds(
  pedidoId: string,
  acao: 'iniciar-preparo' | 'marcar-pronto' | 'refazer' | 'entregar' | 'cancelar',
  versao: number,
  motivo?: string
): Promise<PedidoCozinhaDto> {
  if (!isKdsSimulationMode()) {
    const statusAlvo =
      acao === 'iniciar-preparo' || acao === 'refazer'
        ? 'EM_PREPARO'
        : acao === 'marcar-pronto'
        ? 'PRONTO'
        : acao === 'cancelar'
        ? 'CANCELADO'
        : 'ENTREGUE';

    const res = await httpClient.patch<PedidoApiBackendDto>(
      `/pedidos/${pedidoId}/status`,
      { status: statusAlvo }
    );
    const atualizado = mapPedidoBackendToKds(res.data);
    const evento: PedidoCozinhaEventoDto = {
      seq: Date.now(),
      tipo:
        statusAlvo === 'EM_PREPARO'
          ? 'PREPARO_INICIADO'
          : statusAlvo === 'PRONTO'
          ? 'PEDIDO_PRONTO'
          : 'PEDIDO_ENTREGUE',
      pedidoId: atualizado.pedidoId,
      versao: atualizado.versao,
      status: atualizado.status,
      ocorridoEm: new Date().toISOString(),
      pedido: atualizado,
    };
    publishStompEvent(`/topic/lojas/${LOJA_MATRIZ_ID}/kds`, evento);
    return atualizado;
  }

  const store = loadKdsStore();
  const pedido = store.pedidos.find((p) => p.pedidoId === pedidoId);
  if (!pedido) {
    const err: ProblemDetail = {
      type: 'https://api.cafeteria.local/erros/recurso-nao-encontrado',
      title: 'Pedido não encontrado',
      status: 404,
      code: 'RECURSO_NAO_ENCONTRADO',
      detail: `Pedido ${pedidoId} não localizado na fila da cozinha.`,
    };
    throw err;
  }

  if (pedido.versao !== versao) {
    const err: ProblemDetail = {
      type: 'https://api.cafeteria.local/erros/transicao-concorrente',
      title: 'Transição concorrente detectada',
      status: 409,
      code: 'TRANSICAO_CONCORRENTE',
      detail: `O pedido #${pedido.numeroDiario} já foi alterado por outro terminal para ${pedido.status} (v${pedido.versao}).`,
      estadoAtual: {
        status: pedido.status,
        versao: pedido.versao,
      },
    };
    throw err;
  }

  const agora = new Date().toISOString();
  let tipoEvento = 'PREPARO_INICIADO';

  if (acao === 'iniciar-preparo') {
    pedido.status = 'EM_PREPARO';
    pedido.preparoIniciadoEm = agora;
    tipoEvento = 'PREPARO_INICIADO';
  } else if (acao === 'marcar-pronto') {
    pedido.status = 'PRONTO';
    pedido.prontoEm = agora;
    tipoEvento = 'PEDIDO_PRONTO';
  } else if (acao === 'refazer') {
    pedido.status = 'EM_PREPARO';
    pedido.prontoEm = null;
    tipoEvento = 'PEDIDO_REFEITO';
  } else if (acao === 'entregar') {
    pedido.status = 'ENTREGUE';
    tipoEvento = 'PEDIDO_ENTREGUE';
  }

  pedido.versao += 1;
  store.seqAtual += 1;

  const evento: PedidoCozinhaEventoDto = {
    seq: store.seqAtual,
    tipo: tipoEvento,
    pedidoId: pedido.pedidoId,
    versao: pedido.versao,
    status: pedido.status,
    ocorridoEm: agora,
    motivo: motivo || null,
    pedido: { ...pedido },
  };
  store.eventosOutbox.push(evento);
  saveKdsStore(store);

  // Propaga a transição para o Backend Bridge (:8080) para sincronizar com o Frontend Master (:5173)
  try {
    await httpClient.post(`/bridge/kds/orders/${pedidoId}/transition`, {
      acao,
      versao,
      motivo: motivo || null,
      pedidoSnapshot: pedido,
    });
  } catch {
    // fallback local
  }

  publishStompEvent(`/topic/lojas/${LOJA_MATRIZ_ID}/kds`, evento);
  publishStompEvent(`/topic/lojas/${LOJA_MATRIZ_ID}/cozinha/pedidos`, evento);

  if (acao === 'marcar-pronto') {
    const prontoEv: PedidoProntoEventoDto = {
      pedidoId: pedido.pedidoId,
      numeroDiario: pedido.numeroDiario,
      identificadorCliente: pedido.identificadorCliente,
      mesa: pedido.mesa,
    };
    publishStompEvent(
      `/topic/lojas/${LOJA_MATRIZ_ID}/pedidos/prontos`,
      prontoEv
    );
  }

  return { ...pedido };
}

/**
 * Simula a chegada de um novo pedido (ou um evento criado enquanto o KDS estava desconectado)
 * para testar o Replay Monotônico (`GET /api/v1/cozinha/eventos?desdeSeq=...`).
 */
export function simularNovoPedidoNaOutbox(
  publicarNoWebsocketImediatamente: boolean
): PedidoCozinhaEventoDto {
  const store = loadKdsStore();
  const maxNumero = store.pedidos.reduce(
    (acc, p) => Math.max(acc, p.numeroDiario),
    100
  );
  const agora = new Date().toISOString();
  const clientes = [
    { nome: 'Mariana Costa', tipo: 'BALCAO' as const, mesa: null },
    { nome: 'Rafael Nogueira', tipo: 'MESA' as const, mesa: '07' },
    { nome: 'Juliana Mendes', tipo: 'DELIVERY' as const, mesa: null },
  ];
  const sorteado = clientes[maxNumero % clientes.length];

  const novoPedido: PedidoCozinhaDto = {
    pedidoId: generateUuidV7(),
    numeroDiario: maxNumero + 1,
    identificadorCliente: sorteado.nome,
    mesa: sorteado.mesa,
    tipoAtendimento: sorteado.tipo,
    status: 'CONFIRMADO',
    versao: 1,
    observacao:
      maxNumero % 2 === 0
        ? 'Atenção: Alergia a lactose — usar jarra exclusiva para leite vegetal'
        : 'Caprichar na arte latte',
    confirmadoEm: agora,
    preparoIniciadoEm: null,
    prontoEm: null,
    itens: [
      {
        itemId: generateUuidV7(),
        produtoNome: 'Flat White Duplo Especial',
        quantidade: 2,
        estacao: 'BAR',
        modificadores: [
          'Leite de Aveia Barista',
          '1x Shot Extra Espresso Arábica',
        ],
        observacao: 'Temperatura 65°C',
        comboNome: null,
      },
      {
        itemId: generateUuidV7(),
        produtoNome: 'Croissant Amêndoas Tostadas',
        quantidade: 1,
        estacao: 'COZINHA',
        modificadores: ['Aquecido na hora'],
        observacao: null,
        comboNome: null,
      },
    ],
  };

  store.pedidos.push(novoPedido);
  store.seqAtual += 1;

  const evento: PedidoCozinhaEventoDto = {
    seq: store.seqAtual,
    tipo: 'PEDIDO_CONFIRMADO',
    pedidoId: novoPedido.pedidoId,
    versao: novoPedido.versao,
    status: novoPedido.status,
    ocorridoEm: agora,
    pedido: novoPedido,
  };
  store.eventosOutbox.push(evento);
  saveKdsStore(store);

  if (publicarNoWebsocketImediatamente) {
    publishStompEvent(`/topic/lojas/${LOJA_MATRIZ_ID}/kds`, evento);
    publishStompEvent(
      `/topic/lojas/${LOJA_MATRIZ_ID}/cozinha/pedidos`,
      evento
    );
  }

  return evento;
}

// ─────────────────────────── Insumos (Controle Rápido de Ruptura 86) ───────────────────────────

export async function listarInsumosKds(): Promise<Insumo[]> {
  if (!isKdsSimulationMode()) {
    try {
      const res = await httpClient.get<Insumo[]>('/catalogo/insumos');
      return res.data;
    } catch {
      // fallback
    }
  }
  return loadKdsStore().insumos;
}

export async function alternarDisponibilidadeInsumoKds(
  insumoId: string,
  disponivel: boolean,
  motivo?: string
): Promise<InsumoDisponibilidadeResponse> {
  if (!isKdsSimulationMode()) {
    const res = await httpClient.patch<InsumoDisponibilidadeResponse>(
      `/catalogo/insumos/${insumoId}/disponibilidade`,
      {
        disponivel,
        motivo: disponivel
          ? null
          : motivo || 'Pausado pela cozinha no KDS (86)',
      }
    );
    return res.data;
  }

  const store = loadKdsStore();
  const insumo = store.insumos.find((i) => i.id === insumoId);
  if (!insumo) {
    throw new Error('Insumo não encontrado.');
  }

  insumo.disponivel = disponivel;
  insumo.motivoIndisponibilidade = disponivel
    ? null
    : motivo || 'Esgotado na estação de preparo (86)';
  insumo.versao += 1;
  saveKdsStore(store);

  // Sincroniza também com o store do Frontend Master (se estiver no mesmo navegador)
  try {
    const masterRaw = localStorage.getItem('orderflow:master_sim_store_v1');
    if (masterRaw) {
      const masterStore = JSON.parse(masterRaw);
      const target = masterStore.insumos?.find(
        (x: Insumo) => x.id === insumoId
      );
      if (target) {
        target.disponivel = disponivel;
        target.motivoIndisponibilidade = insumo.motivoIndisponibilidade;
        localStorage.setItem(
          'orderflow:master_sim_store_v1',
          JSON.stringify(masterStore)
        );
      }
    }
  } catch {
    // ignora
  }

  publishStompEvent(`/topic/lojas/${LOJA_MATRIZ_ID}/disponibilidade`, {
    versao: Date.now(),
    tipo: 'DISPONIBILIDADE_ALTERADA',
    ocorridoEm: new Date().toISOString(),
    disponibilidade: [
      {
        entidade: 'INSUMO',
        id: insumo.id,
        disponivel: insumo.disponivel,
        motivo: insumo.motivoIndisponibilidade,
      },
    ],
  });

  return {
    insumo,
    catalogoVersao: 105,
    produtosAfetados: [
      {
        id: 'prod-ref',
        nome: `Produtos dependentes de ${insumo.nome}`,
        disponivel,
      },
    ],
    modificadoresAfetados: [
      {
        id: 'mod-ref',
        nome: `Adicionais dependentes de ${insumo.nome}`,
        disponivel,
      },
    ],
  };
}

// ─────────────────────────── Gerenciador STOMP do KDS ───────────────────────────

export class KdsStompConnection {
  private client: Client | null = null;
  private eventHandler: ((ev: PedidoCozinhaEventoDto) => void) | null = null;
  private statusHandler: ((connected: boolean) => void) | null = null;
  private domListeners: Array<() => void> = [];
  private pollTimer: number | null = null;
  private processedBridgeKeys = new Set<string>();

  public start(
    estacao: FiltroEstacaoKds,
    onEvent: (ev: PedidoCozinhaEventoDto) => void,
    onStatusChange: (connected: boolean) => void
  ): void {
    this.stop();
    this.eventHandler = onEvent;
    this.statusHandler = onStatusChange;

    // 1. Escuta o barramento local / BroadcastChannel
    const destKds = `/topic/lojas/${LOJA_MATRIZ_ID}/kds`;
    const listener = (e: Event) => {
      const custom = e as CustomEvent<PedidoCozinhaEventoDto>;
      if (custom.detail) {
        const key = `${custom.detail.pedidoId}:${custom.detail.versao}:${custom.detail.status}`;
        if (!this.processedBridgeKeys.has(key)) {
          this.processedBridgeKeys.add(key);
          this.eventHandler?.(custom.detail);
        }
      }
    };
    localBus.addEventListener(destKds, listener);
    this.domListeners.push(() =>
      localBus.removeEventListener(destKds, listener)
    );

    // 2. Polling reativo em tempo real sobre GET /pedidos (:8080) alinhado ao Monitor do Sistema Master
    this.pollTimer = window.setInterval(async () => {
      try {
        const res = await httpClient.get<PedidoApiBackendDto[]>('/pedidos');
        if (Array.isArray(res.data)) {
          this.statusHandler?.(true);
          for (const raw of res.data) {
            const mapped = mapPedidoBackendToKds(raw);
            const key = `${mapped.pedidoId}:${mapped.versao}:${mapped.status}`;
            if (!this.processedBridgeKeys.has(key)) {
              const isFirstTimeForOrder = !Array.from(this.processedBridgeKeys).some(
                (k) => k.startsWith(`${mapped.pedidoId}:`)
              );
              this.processedBridgeKeys.add(key);

              const tipoEv =
                mapped.status === 'RECEBIDO' || mapped.status === 'CONFIRMADO'
                  ? isFirstTimeForOrder
                    ? 'PEDIDO_CONFIRMADO'
                    : 'PREPARO_INICIADO'
                  : mapped.status === 'EM_PREPARO'
                  ? 'PREPARO_INICIADO'
                  : mapped.status === 'PRONTO'
                  ? 'PEDIDO_PRONTO'
                  : mapped.status === 'ENTREGUE'
                  ? 'PEDIDO_ENTREGUE'
                  : 'PEDIDO_CANCELADO';

              const ev: PedidoCozinhaEventoDto = {
                seq: raw.id + (raw.versao ?? 0),
                tipo: tipoEv,
                pedidoId: mapped.pedidoId,
                versao: mapped.versao,
                status: mapped.status,
                ocorridoEm: mapped.confirmadoEm,
                pedido: mapped,
              };
              this.eventHandler?.(ev);
            }
          }
        }
      } catch {
        // ignora oscilações transitórias de rede sem piscar a tela
      }
    }, 3000);

    // 3. Conexão STOMP opcional (ativada apenas quando VITE_ENABLE_WS === 'true' para evitar ECONNRESET no proxy Vite)
    if (import.meta.env.VITE_ENABLE_WS === 'true') {
      const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
      const brokerURL =
        import.meta.env.VITE_WS_URL ||
        `${wsProtocol}//${window.location.host}/ws`;
      const token = localStorage.getItem('orderflow:access_token') || '';

      this.client = new Client({
        brokerURL,
        connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
        heartbeatIncoming: 10000,
        heartbeatOutgoing: 10000,
        reconnectDelay: 5000,
        onConnect: () => {
          this.statusHandler?.(true);
          const topico =
            estacao === 'TODAS'
              ? `/topic/lojas/${LOJA_MATRIZ_ID}/cozinha/pedidos`
              : `/topic/lojas/${LOJA_MATRIZ_ID}/cozinha/estacoes/${
                  estacao as EstacaoPreparo
                }`;

          this.client?.subscribe(topico, (msg: IMessage) => {
            try {
              const parsed = JSON.parse(msg.body) as PedidoCozinhaEventoDto;
              const key = `${parsed.pedidoId}:${parsed.versao}:${parsed.status}`;
              if (!this.processedBridgeKeys.has(key)) {
                this.processedBridgeKeys.add(key);
                this.eventHandler?.(parsed);
              }
            } catch {
              // ignora payload inválido
            }
          });
        },
        onDisconnect: () => this.statusHandler?.(true),
        onWebSocketClose: () => this.statusHandler?.(true),
        onStompError: () => this.statusHandler?.(true),
      });

      this.client.activate();
    }
    this.statusHandler?.(true);
  }

  public stop(): void {
    if (this.pollTimer !== null) {
      window.clearInterval(this.pollTimer);
      this.pollTimer = null;
    }
    for (const unsub of this.domListeners) {
      unsub();
    }
    this.domListeners = [];
    if (this.client) {
      this.client.deactivate();
      this.client = null;
    }
  }
}
