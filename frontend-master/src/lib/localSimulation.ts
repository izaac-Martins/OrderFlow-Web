// ═══════════════════════════════════════════════════════════════════════════
// Motor de Simulação Local Fiel aos Contratos OpenAPI e Catálogo de Erros RFC 7807
// Permite operar e homologar 100% dos fluxos mesmo quando o backend MySQL/Spring
// não estiver ativo localmente, disparando os mesmos eventos STOMP e códigos RFC 7807.
// ═══════════════════════════════════════════════════════════════════════════

import type { InternalAxiosRequestConfig } from 'axios';
import type {
  CatalogoEventoStomp,
  Categoria,
  CategoriaRequest,
  CodigoErro,
  ComboSlot,
  ConferenciaMetodoItem,
  ConferenciaResponse,
  CriarPedidoRequest,
  FechamentoCegoRequest,
  FechamentoProtocoloResponse,
  GrupoModificador,
  HistoricoPreco,
  Insumo,
  InsumoDisponibilidadeResponse,
  InsumoRequest,
  ItemPedidoModificadorResponse,
  ItemPedidoResponse,
  MetodoPagamento,
  MovimentacaoGerencial,
  MovimentacaoOperador,
  PagamentoRequest,
  PagamentoResponse,
  PedidoCozinhaEventoDto,
  PedidoProntoEventoDto,
  PedidoResponse,
  ProblemDetail,
  Produto,
  ProdutoRequest,
  SessaoCaixaGerencial,
  SessaoCaixaOperador,
  SituacaoConferencia,
  StatusPedido,
  TerminalCaixa,
  TokenResponse,
  UserRole,
  UsuarioAutenticado,
  ValidarPinResponse,
} from '../types/api';
import { calcPercentualHalfEven, generateUuidV7 } from './formatters';
import { emitSimulatedStompMessage } from './stompClient';

const LOJA_MATRIZ_ID = '019325f0-8a00-7000-8000-000000000001';
const LIMITE_SANGRIA_SEM_PIN_CENTAVOS = 50_000; // R$ 500,00
const PINS_GERENCIAIS_VALIDOS = new Set(['1234', '4321', '0000']);

function throwProblem(
  code: CodigoErro,
  status: number,
  detail: string,
  extra?: Partial<ProblemDetail>
): never {
  const problem: ProblemDetail = {
    type: `https://api.cafeteria.local/erros/${code.toLowerCase().replace(/_/g, '-')}`,
    title: code,
    status,
    code,
    detail,
    traceId: generateUuidV7().replace(/-/g, ''),
    erros: [],
    itensIndisponiveis: [],
    ...extra,
  };
  throw problem;
}

// ─────────────────────────── Estado Inicial de Homologação ───────────────────────────

interface SimulationStore {
  catalogoVersao: number;
  seqEventoKds: number;
  usuarioAtual: UsuarioAutenticado;
  categorias: Categoria[];
  insumos: Insumo[];
  gruposModificadores: GrupoModificador[];
  produtos: Produto[];
  historicoPrecos: HistoricoPreco[];
  terminais: TerminalCaixa[];
  sessoes: SessaoCaixaGerencial[];
  movimentacoes: MovimentacaoGerencial[];
  declaracoesPorSessao: Record<
    string,
    Array<{
      metodo: MetodoPagamento;
      declaradoCentavos: number;
      esperadoCentavos: number;
      quantidadeComprovantes?: number | null;
    }>
  >;
  observacoesSessao: Record<
    string,
    { operador?: string | null; conferencia?: string | null }
  >;
  pedidos: PedidoResponse[];
}

const initialInsumos: Insumo[] = [
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

const initialCategorias: Categoria[] = [
  {
    id: '019325f0-8a00-7000-8002-000000000001',
    parentId: null,
    categoriaPaiId: null,
    nome: 'Cafés Especiais',
    slug: 'cafes-especiais',
    ordem: 1,
    ordemExibicao: 1,
    ativo: true,
    arquivada: false,
    estacaoPreparoPadrao: 'BAR',
    versao: 1,
  },
  {
    id: '019325f0-8a00-7000-8002-000000000002',
    parentId: '019325f0-8a00-7000-8002-000000000001',
    categoriaPaiId: '019325f0-8a00-7000-8002-000000000001',
    nome: 'Latte & Cappuccinos',
    slug: 'latte-e-cappuccinos',
    ordem: 1,
    ordemExibicao: 1,
    ativo: true,
    arquivada: false,
    estacaoPreparoPadrao: 'BAR',
    versao: 1,
  },
  {
    id: '019325f0-8a00-7000-8002-000000000003',
    parentId: null,
    categoriaPaiId: null,
    nome: 'Viennoiserie & Padaria',
    slug: 'viennoiserie-padaria',
    ordem: 2,
    ordemExibicao: 2,
    ativo: true,
    arquivada: false,
    estacaoPreparoPadrao: 'COZINHA',
    versao: 1,
  },
  {
    id: '019325f0-8a00-7000-8002-000000000004',
    parentId: null,
    categoriaPaiId: null,
    nome: 'Combos OrderFlow',
    slug: 'combos-orderflow',
    ordem: 3,
    ordemExibicao: 3,
    ativo: true,
    arquivada: false,
    estacaoPreparoPadrao: 'NENHUMA',
    versao: 1,
  },
];

const initialGrupos: GrupoModificador[] = [
  {
    id: '019325f0-8a00-7000-8003-000000000001',
    nome: 'Escolha o Leite',
    nomeInterno: 'tipo-leite',
    minSelecao: 1,
    maxSelecao: 1,
    permiteRepeticao: false,
    ordem: 1,
    ativo: true,
    versao: 1,
    modificadores: [
      {
        id: '019325f0-8a00-7000-8004-000000000001',
        grupoId: '019325f0-8a00-7000-8003-000000000001',
        nome: 'Leite Integral Fazenda',
        tipoPreco: 'NENHUM',
        valorPreco: 0,
        precoAdicionalCentavos: 0,
        padrao: true,
        maxQuantidade: 1,
        ordem: 1,
        ativo: true,
        pausado: false,
        disponivel: true,
        insumoIds: [],
        versao: 1,
      },
      {
        id: '019325f0-8a00-7000-8004-000000000002',
        grupoId: '019325f0-8a00-7000-8003-000000000001',
        nome: 'Leite de Aveia Barista (+R$ 4,00)',
        tipoPreco: 'FIXO',
        valorPreco: 400,
        precoAdicionalCentavos: 400,
        padrao: false,
        maxQuantidade: 1,
        ordem: 2,
        ativo: true,
        pausado: false,
        disponivel: true,
        insumoIds: ['019325f0-8a00-7000-8001-000000000002'],
        versao: 1,
      },
    ],
  },
  {
    id: '019325f0-8a00-7000-8003-000000000002',
    nome: 'Turbine sua Bebida',
    nomeInterno: 'extras-bar',
    minSelecao: 0,
    maxSelecao: 3,
    permiteRepeticao: true,
    ordem: 2,
    ativo: true,
    versao: 1,
    modificadores: [
      {
        id: '019325f0-8a00-7000-8004-000000000003',
        grupoId: '019325f0-8a00-7000-8003-000000000002',
        nome: 'Shot Extra Espresso Arábica',
        tipoPreco: 'FIXO',
        valorPreco: 450,
        precoAdicionalCentavos: 450,
        padrao: false,
        maxQuantidade: 3,
        ordem: 1,
        ativo: true,
        pausado: false,
        disponivel: true,
        insumoIds: ['019325f0-8a00-7000-8001-000000000001'],
        versao: 1,
      },
      {
        id: '019325f0-8a00-7000-8004-000000000004',
        grupoId: '019325f0-8a00-7000-8003-000000000002',
        nome: 'Toque de Caramelo Salgado',
        tipoPreco: 'FIXO',
        valorPreco: 350,
        precoAdicionalCentavos: 350,
        padrao: false,
        maxQuantidade: 2,
        ordem: 2,
        ativo: true,
        pausado: false,
        disponivel: true,
        insumoIds: ['019325f0-8a00-7000-8001-000000000004'],
        versao: 1,
      },
      {
        id: '019325f0-8a00-7000-8004-000000000005',
        grupoId: '019325f0-8a00-7000-8003-000000000002',
        nome: 'Copo Grande 400ml (+15%)',
        tipoPreco: 'PERCENTUAL',
        valorPreco: 1500,
        precoAdicionalCentavos: 0,
        padrao: false,
        maxQuantidade: 1,
        ordem: 3,
        ativo: true,
        pausado: false,
        disponivel: true,
        insumoIds: [],
        versao: 1,
      },
    ],
  },
];

const initialProdutos: Produto[] = [
  {
    id: '019325f0-8a00-7000-8005-000000000001',
    categoriaId: '019325f0-8a00-7000-8002-000000000002',
    sku: 'CAF-CAP-ITA',
    nome: 'Cappuccino Italiano Clássico',
    descricao: 'Espresso duplo 100% arábica com leite vaporizado sedoso e cacau.',
    imagemUrl: null,
    tipo: 'SIMPLES',
    precoBaseCentavos: 1450,
    estacaoPreparo: 'BAR',
    estacaoPreparoEfetiva: 'BAR',
    ativo: true,
    pausado: false,
    disponivel: true,
    motivoIndisponibilidade: null,
    ordem: 1,
    versao: 1,
    insumoIds: ['019325f0-8a00-7000-8001-000000000001'],
    gruposModificadores: [
      {
        grupo: initialGrupos[0],
        ordem: 1,
        minSelecaoOverride: 1,
        maxSelecaoOverride: 1,
        minSelecaoEfetiva: 1,
        maxSelecaoEfetiva: 1,
      },
      {
        grupo: initialGrupos[1],
        ordem: 2,
        minSelecaoOverride: 0,
        maxSelecaoOverride: 3,
        minSelecaoEfetiva: 0,
        maxSelecaoEfetiva: 3,
      },
    ],
    comboSlots: [],
  },
  {
    id: '019325f0-8a00-7000-8005-000000000002',
    categoriaId: '019325f0-8a00-7000-8002-000000000002',
    sku: 'CAF-LAT-AVE',
    nome: 'Oat Milk Latte Especial',
    descricao: 'Espresso curto extraído sobre leite de aveia barista vaporizado.',
    imagemUrl: null,
    tipo: 'SIMPLES',
    precoBaseCentavos: 1790,
    estacaoPreparo: 'BAR',
    estacaoPreparoEfetiva: 'BAR',
    ativo: true,
    pausado: false,
    disponivel: true,
    motivoIndisponibilidade: null,
    ordem: 2,
    versao: 1,
    insumoIds: [
      '019325f0-8a00-7000-8001-000000000001',
      '019325f0-8a00-7000-8001-000000000002',
    ],
    gruposModificadores: [
      {
        grupo: initialGrupos[1],
        ordem: 1,
        minSelecaoOverride: 0,
        maxSelecaoOverride: 3,
        minSelecaoEfetiva: 0,
        maxSelecaoEfetiva: 3,
      },
    ],
    comboSlots: [],
  },
  {
    id: '019325f0-8a00-7000-8005-000000000003',
    categoriaId: '019325f0-8a00-7000-8002-000000000003',
    sku: 'PAD-CRO-MAN',
    nome: 'Croissant Francês na Manteiga',
    descricao: 'Massa folhada artesanal de fermentação longa assada na hora.',
    imagemUrl: null,
    tipo: 'SIMPLES',
    precoBaseCentavos: 1350,
    estacaoPreparo: 'COZINHA',
    estacaoPreparoEfetiva: 'COZINHA',
    ativo: true,
    pausado: false,
    disponivel: true,
    motivoIndisponibilidade: null,
    ordem: 1,
    versao: 1,
    insumoIds: ['019325f0-8a00-7000-8001-000000000003'],
    gruposModificadores: [],
    comboSlots: [],
  },
  {
    id: '019325f0-8a00-7000-8005-000000000004',
    categoriaId: '019325f0-8a00-7000-8002-000000000003',
    sku: 'PAD-PAO-QUE',
    nome: 'Porção Pão de Queijo Canastra (6 un)',
    descricao: 'Assados na hora com queijo meia cura da Serra da Canastra.',
    imagemUrl: null,
    tipo: 'SIMPLES',
    precoBaseCentavos: 1500,
    estacaoPreparo: 'COZINHA',
    estacaoPreparoEfetiva: 'COZINHA',
    ativo: true,
    pausado: false,
    disponivel: true,
    motivoIndisponibilidade: null,
    ordem: 2,
    versao: 1,
    insumoIds: [],
    gruposModificadores: [],
    comboSlots: [],
  },
  {
    id: '019325f0-8a00-7000-8005-000000000005',
    categoriaId: '019325f0-8a00-7000-8002-000000000004',
    sku: 'CMB-MANHA-01',
    nome: 'Combo Manhã Parisiense',
    descricao: '1 Bebida Quente Especial + 1 Viennoiserie/Salgado assado na hora.',
    imagemUrl: null,
    tipo: 'COMBO',
    precoBaseCentavos: 2490,
    estacaoPreparo: 'NENHUMA',
    estacaoPreparoEfetiva: 'NENHUMA',
    ativo: true,
    pausado: false,
    disponivel: true,
    motivoIndisponibilidade: null,
    ordem: 1,
    versao: 1,
    insumoIds: [],
    gruposModificadores: [],
    comboSlots: [
      {
        id: '019325f0-8a00-7000-8006-000000000001',
        nome: 'Escolha sua Bebida',
        minSelecao: 1,
        maxSelecao: 1,
        obrigatorio: true,
        ordem: 1,
        opcoes: [
          {
            produtoId: '019325f0-8a00-7000-8005-000000000001',
            produtoNome: 'Cappuccino Italiano Clássico',
            acrescimoCentavos: 0,
            padrao: true,
            ordem: 1,
            disponivel: true,
          },
          {
            produtoId: '019325f0-8a00-7000-8005-000000000002',
            produtoNome: 'Oat Milk Latte Especial',
            acrescimoCentavos: 300,
            padrao: false,
            ordem: 2,
            disponivel: true,
          },
        ],
      },
      {
        id: '019325f0-8a00-7000-8006-000000000002',
        nome: 'Escolha seu Acompanhamento',
        minSelecao: 1,
        maxSelecao: 1,
        obrigatorio: true,
        ordem: 2,
        opcoes: [
          {
            produtoId: '019325f0-8a00-7000-8005-000000000003',
            produtoNome: 'Croissant Francês na Manteiga',
            acrescimoCentavos: 0,
            padrao: true,
            ordem: 1,
            disponivel: true,
          },
          {
            produtoId: '019325f0-8a00-7000-8005-000000000004',
            produtoNome: 'Porção Pão de Queijo Canastra (6 un)',
            acrescimoCentavos: 150,
            padrao: false,
            ordem: 2,
            disponivel: true,
          },
        ],
      },
    ],
  },
];

const initialTerminais: TerminalCaixa[] = [
  {
    id: '019325f0-8a00-7000-8007-000000000001',
    nome: 'PDV 01 — Balcão Principal',
    possuiGaveta: true,
    ativo: true,
    sessaoAbertaId: null,
    operadorAtual: null,
  },
  {
    id: '019325f0-8a00-7000-8007-000000000002',
    nome: 'PDV 02 — Deck & Drive',
    possuiGaveta: true,
    ativo: true,
    sessaoAbertaId: null,
    operadorAtual: null,
  },
];

const defaultUser: UsuarioAutenticado = {
  id: '019325f0-8a00-7000-8009-000000000001',
  lojaId: LOJA_MATRIZ_ID,
  codigoLoja: 'MATRIZ-01',
  nomeLoja: 'Cafeteria OrderFlow — Unidade Matriz Jardins',
  nome: 'Roberto Almeida (Gerente Geral)',
  email: 'gerente@orderflow.local',
  role: 'GERENTE',
  permissions: [
    'catalogo:ler',
    'catalogo:escrever',
    'catalogo:preco',
    'insumo:disponibilidade',
    'caixa:operar',
    'caixa:conferir',
    'caixa:gerenciar',
    'pedido:criar',
    'pedido:ler',
    'pedido:confirmar',
    'pedido:entregar',
    'pedido:cancelar_inicial',
    'pedido:cancelar_forcado',
    'pagamento:registrar',
    'pagamento:estornar',
    'cozinha:operar',
  ],
  possuiPinAlcada: true,
};

const STORE_KEY = 'orderflow:master_sim_store_v1';

function loadStore(): SimulationStore {
  try {
    const raw = localStorage.getItem(STORE_KEY);
    if (raw) {
      return JSON.parse(raw) as SimulationStore;
    }
  } catch {
    // fallback para estado inicial
  }
  const initial: SimulationStore = {
    catalogoVersao: 101,
    seqEventoKds: 10,
    usuarioAtual: defaultUser,
    categorias: initialCategorias,
    insumos: initialInsumos,
    gruposModificadores: initialGrupos,
    produtos: initialProdutos,
    historicoPrecos: [
      {
        id: '019325f0-8a00-7000-8010-000000000001',
        entidadeTipo: 'PRODUTO',
        entidadeId: '019325f0-8a00-7000-8005-000000000001',
        tipoPrecoAnterior: 'FIXO',
        valorAnterior: 1350,
        tipoPrecoNovo: 'FIXO',
        valorNovo: 1450,
        motivo: 'Reajuste safra grãos especiais 2026',
        vigenteDesde: new Date(Date.now() - 86_400_000 * 5).toISOString(),
        alteradoPor: 'Roberto Almeida',
      },
    ],
    terminais: initialTerminais,
    sessoes: [],
    movimentacoes: [],
    declaracoesPorSessao: {},
    observacoesSessao: {},
    pedidos: [],
  };
  saveStore(initial);
  return initial;
}

function saveStore(store: SimulationStore): void {
  localStorage.setItem(STORE_KEY, JSON.stringify(store));
}

export function resetSimulationStore(): void {
  localStorage.removeItem(STORE_KEY);
  loadStore();
}

// ─────────────────────────── Cálculo de Disponibilidade em Cascata ───────────────────────────

function recalcularCascataDisponibilidade(store: SimulationStore): void {
  const insumoMap = new Map(store.insumos.map((i) => [i.id, i]));

  for (const grupo of store.gruposModificadores) {
    for (const mod of grupo.modificadores) {
      const todosInsumosOk = (mod.insumoIds || []).every(
        (id) => insumoMap.get(id)?.disponivel !== false
      );
      mod.disponivel = mod.ativo && !mod.pausado && todosInsumosOk;
    }
  }

  const prodSimplesMap = new Map<string, boolean>();
  for (const prod of store.produtos) {
    if (prod.tipo === 'SIMPLES') {
      const insumosOk = (prod.insumoIds || []).every(
        (id) => insumoMap.get(id)?.disponivel !== false
      );
      const insumoFaltante = (prod.insumoIds || [])
        .map((id) => insumoMap.get(id))
        .find((i) => i && !i.disponivel);

      prod.disponivel = prod.ativo && !prod.pausado && insumosOk;
      prod.motivoIndisponibilidade = !insumosOk && insumoFaltante
        ? `Insumo esgotado: ${insumoFaltante.nome}`
        : prod.pausado
        ? prod.motivoIndisponibilidade || 'Pausado manualmente'
        : null;
      prodSimplesMap.set(prod.id, prod.disponivel);
    }
  }

  for (const prod of store.produtos) {
    if (prod.tipo === 'COMBO') {
      let slotsValidos = true;
      for (const slot of prod.comboSlots || []) {
        let disponiveisNoSlot = 0;
        for (const op of slot.opcoes) {
          op.disponivel = prodSimplesMap.get(op.produtoId) ?? true;
          if (op.disponivel) disponiveisNoSlot += 1;
        }
        if (slot.minSelecao > 0 && disponiveisNoSlot < slot.minSelecao) {
          slotsValidos = false;
        }
      }
      prod.disponivel = prod.ativo && !prod.pausado && slotsValidos;
      prod.motivoIndisponibilidade = !slotsValidos
        ? 'Opção obrigatória do combo está esgotada'
        : null;
    }
  }
}

// ─────────────────────────── Roteador de Requisições Simuladas ───────────────────────────

export async function handleLocalSimulationRequest(
  config: InternalAxiosRequestConfig
): Promise<unknown> {
  const store = loadStore();
  recalcularCascataDisponibilidade(store);

  const method = (config.method || 'get').toUpperCase();
  const rawUrl = (config.url || '').replace(/^https?:\/\/[^/]+/, '');
  const path = rawUrl.replace(/^\/api\/v1/, '').split('?')[0];
  const body =
    typeof config.data === 'string' && config.data
      ? JSON.parse(config.data)
      : config.data || {};

  // 1. AUTENTICAÇÃO & PIN DE ALÇADA
  if (method === 'POST' && path === '/auth/login') {
    const email = String(body.email || '').toLowerCase();
    const senha = String(body.senha || '');
    if (senha.length < 3) {
      throwProblem('CREDENCIAIS_INVALIDAS', 401, 'E-mail ou senha inválidos.');
    }

    let role: UserRole = 'GERENTE';
    let nome = 'Roberto Almeida (Gerente)';
    if (email.includes('admin')) {
      role = 'ADMIN';
      nome = 'Helena Costa (Admin)';
    } else if (email.includes('caixa') || email.includes('operador')) {
      role = 'OPERADOR_CAIXA';
      nome = 'Lucas Martins (Operador de Caixa)';
    } else if (email.includes('atendente') || email.includes('lucas')) {
      role = 'ATENDENTE';
      nome = 'Marina Souza (Atendente PDV)';
    } else if (email.includes('cozinha') || email.includes('kds')) {
      role = 'COZINHA';
      nome = 'Chef Barista KDS';
    }

    const usuario: UsuarioAutenticado = {
      id: generateUuidV7(),
      lojaId: body.lojaId || LOJA_MATRIZ_ID,
      codigoLoja: body.codigoLoja || 'MATRIZ-01',
      nomeLoja:
        body.codigoLoja === 'FILIAL-02'
          ? 'Cafeteria OrderFlow — Filial Paulista'
          : 'Cafeteria OrderFlow — Unidade Matriz Jardins',
      nome,
      email: body.email || 'gerente@orderflow.local',
      role,
      permissions: defaultUser.permissions,
      possuiPinAlcada: role === 'ADMIN' || role === 'GERENTE',
    };

    store.usuarioAtual = usuario;
    saveStore(store);

    const resp: TokenResponse = {
      accessToken: `sim-jwt-${generateUuidV7()}`,
      refreshToken: `sim-refresh-${generateUuidV7()}`,
      tokenType: 'Bearer',
      expiresIn: 900,
      usuario,
    };
    return resp;
  }

  if (method === 'POST' && path === '/auth/validar-pin') {
    const pin = String(body.pin || '').trim();
    if (!PINS_GERENCIAIS_VALIDOS.has(pin)) {
      throwProblem(
        'PIN_APROVADOR_INVALIDO',
        403,
        'PIN informado não corresponde a nenhum GERENTE ou ADMIN ativo desta loja.'
      );
    }
    const resp: ValidarPinResponse = {
      valido: true,
      aprovadorId: '019325f0-8a00-7000-8009-000000000001',
      aprovadorNome: 'Roberto Almeida (Gerente Geral)',
      role: 'GERENTE',
      validadoEm: new Date().toISOString(),
    };
    return resp;
  }

  // 2. CATEGORIAS (Hierarquia máx. 2 níveis)
  if (method === 'GET' && (path === '/categorias' || path === '/catalogo/categorias')) {
    return store.categorias.filter((c) => !c.arquivada);
  }

  if (method === 'POST' && (path === '/categorias' || path === '/catalogo/categorias')) {
    const req = body as CategoriaRequest;
    const parentId = req.parentId || null;

    if (parentId) {
      const parent = store.categorias.find((c) => c.id === parentId && !c.arquivada);
      if (!parent) {
        throwProblem('RECURSO_NAO_ENCONTRADO', 404, 'Categoria pai não encontrada.');
      }
      if (parent.parentId) {
        throwProblem(
          'CATEGORIA_PROFUNDIDADE_EXCEDIDA',
          422,
          'A hierarquia de categorias suporta no máximo 2 níveis (Categoria → Subcategoria).'
        );
      }
    }

    const slugDuplicado = store.categorias.some(
      (c) =>
        !c.arquivada &&
        (c.parentId || null) === parentId &&
        c.slug.toLowerCase() === req.slug.trim().toLowerCase()
    );
    if (slugDuplicado) {
      throwProblem(
        'SLUG_DUPLICADO',
        409,
        `Já existe uma categoria com o slug '${req.slug}' neste nível.`
      );
    }

    const nova: Categoria = {
      id: generateUuidV7(),
      parentId,
      categoriaPaiId: parentId,
      nome: req.nome.trim(),
      slug: req.slug.trim().toLowerCase(),
      ordem: req.ordemExibicao || 1,
      ordemExibicao: req.ordemExibicao || 1,
      ativo: req.ativo ?? true,
      arquivada: false,
      estacaoPreparoPadrao: req.estacaoPreparoPadrao || 'BAR',
      versao: 1,
    };
    store.categorias.push(nova);
    store.catalogoVersao += 1;
    saveStore(store);
    return nova;
  }

  const deleteCatMatch = path.match(/^\/(?:catalogo\/)?categorias\/([^/]+)$/);
  if (method === 'DELETE' && deleteCatMatch) {
    const catId = deleteCatMatch[1];
    const possuiProdutos = store.produtos.some((p) => p.categoriaId === catId && p.ativo);
    if (possuiProdutos) {
      throwProblem(
        'CATEGORIA_COM_PRODUTOS_ATIVOS',
        409,
        'Não é possível arquivar uma categoria que possui produtos ativos vinculados.'
      );
    }
    const possuiSubcategorias = store.categorias.some(
      (c) => c.parentId === catId && !c.arquivada
    );
    if (possuiSubcategorias) {
      throwProblem(
        'CATEGORIA_COM_PRODUTOS_ATIVOS',
        409,
        'Arquive as subcategorias antes de arquivar a categoria principal.'
      );
    }
    const cat = store.categorias.find((c) => c.id === catId);
    if (cat) {
      cat.arquivada = true;
      store.catalogoVersao += 1;
      saveStore(store);
    }
    return null;
  }

  // 3. INSUMOS & TOGGLE RÁPIDO DE DISPONIBILIDADE (86) COM BROADCAST STOMP
  if (method === 'GET' && (path === '/insumos' || path === '/catalogo/insumos')) {
    return store.insumos;
  }

  if (method === 'POST' && (path === '/insumos' || path === '/catalogo/insumos')) {
    const req = body as InsumoRequest;
    if (
      store.insumos.some((i) => i.nome.toLowerCase() === req.nome.trim().toLowerCase())
    ) {
      throwProblem('NOME_DUPLICADO', 409, 'Já existe um insumo com este nome na loja.');
    }
    const novo: Insumo = {
      id: generateUuidV7(),
      nome: req.nome.trim(),
      unidade: req.unidadeMedida,
      unidadeMedida: req.unidadeMedida,
      quantidadeAtual: req.quantidadeAtual ?? 0,
      estoqueMinimo: req.estoqueMinimo ?? 0,
      disponivel: req.disponivel ?? true,
      motivoIndisponibilidade: req.motivoIndisponibilidade || null,
      versao: 1,
    };
    store.insumos.push(novo);
    recalcularCascataDisponibilidade(store);
    store.catalogoVersao += 1;
    saveStore(store);
    return novo;
  }

  const insumoDispMatch = path.match(/^\/(?:catalogo\/)?insumos\/([^/]+)\/disponibilidade$/);
  if (method === 'PATCH' && insumoDispMatch) {
    const insumoId = insumoDispMatch[1];
    const insumo = store.insumos.find((i) => i.id === insumoId);
    if (!insumo) {
      throwProblem('RECURSO_NAO_ENCONTRADO', 404, 'Insumo não encontrado.');
    }
    insumo.disponivel = Boolean(body.disponivel);
    insumo.motivoIndisponibilidade = insumo.disponivel
      ? null
      : body.motivo || 'Esgotado na operação (86)';
    insumo.versao += 1;

    recalcularCascataDisponibilidade(store);
    store.catalogoVersao += 1;
    saveStore(store);

    const produtosAfetados = store.produtos
      .filter(
        (p) =>
          (p.insumoIds || []).includes(insumoId) ||
          (p.tipo === 'COMBO' &&
            p.comboSlots.some((s) =>
              s.opcoes.some((o) => {
                const pf = store.produtos.find((x) => x.id === o.produtoId);
                return pf && (pf.insumoIds || []).includes(insumoId);
              })
            ))
      )
      .map((p) => ({ id: p.id, nome: p.nome, disponivel: p.disponivel }));

    const modificadoresAfetados = store.gruposModificadores
      .flatMap((g) => g.modificadores)
      .filter((m) => (m.insumoIds || []).includes(insumoId))
      .map((m) => ({ id: m.id, nome: m.nome, disponivel: m.disponivel }));

    const eventoStomp: CatalogoEventoStomp = {
      versao: store.catalogoVersao,
      tipo: 'DISPONIBILIDADE_ALTERADA',
      ocorridoEm: new Date().toISOString(),
      disponibilidade: [
        {
          entidade: 'INSUMO',
          id: insumo.id,
          disponivel: insumo.disponivel,
          motivo: insumo.motivoIndisponibilidade,
        },
        ...produtosAfetados.map((p) => ({
          entidade: 'PRODUTO' as const,
          id: p.id,
          disponivel: p.disponivel,
          motivo: insumo.motivoIndisponibilidade,
        })),
      ],
    };
    emitSimulatedStompMessage(
      `/topic/lojas/${LOJA_MATRIZ_ID}/disponibilidade`,
      eventoStomp
    );

    const resp: InsumoDisponibilidadeResponse = {
      insumo,
      catalogoVersao: store.catalogoVersao,
      produtosAfetados,
      modificadoresAfetados,
    };
    return resp;
  }

  // 4. GRUPOS DE MODIFICADORES & PRODUTOS
  if (
    method === 'GET' &&
    (path === '/grupos-modificadores' || path === '/catalogo/grupos-modificadores')
  ) {
    return store.gruposModificadores;
  }

  if (method === 'GET' && (path === '/produtos' || path === '/catalogo/produtos')) {
    return store.produtos;
  }

  if (method === 'POST' && (path === '/produtos' || path === '/catalogo/produtos')) {
    const req = body as ProdutoRequest;
    if (req.precoBaseCentavos < 0) {
      throwProblem(
        'PRECO_INVALIDO',
        422,
        'O preço base em centavos não pode ser negativo.'
      );
    }
    if (
      store.produtos.some((p) => p.sku.toLowerCase() === req.sku.trim().toLowerCase())
    ) {
      throwProblem('SKU_DUPLICADO', 409, `Já existe um produto com o SKU '${req.sku}'.`);
    }

    const novo: Produto = {
      id: generateUuidV7(),
      categoriaId: req.categoriaId,
      sku: req.sku.trim().toUpperCase(),
      nome: req.nome.trim(),
      descricao: req.descricao || null,
      imagemUrl: req.imagemUrl || null,
      tipo: req.tipo,
      precoBaseCentavos: req.precoBaseCentavos,
      estacaoPreparo: req.estacaoPreparo || 'BAR',
      estacaoPreparoEfetiva: req.estacaoPreparo || 'BAR',
      ativo: req.ativo ?? true,
      pausado: !(req.disponivel ?? true),
      disponivel: req.disponivel ?? true,
      motivoIndisponibilidade: null,
      ordem: req.ordem || 1,
      versao: 1,
      insumoIds: [],
      gruposModificadores: [],
      comboSlots: [],
    };
    store.produtos.push(novo);
    recalcularCascataDisponibilidade(store);
    store.catalogoVersao += 1;
    saveStore(store);
    return novo;
  }

  const prodSlotsMatch = path.match(/^\/(?:catalogo\/)?produtos\/([^/]+)\/combo-slots$/);
  if (method === 'PUT' && prodSlotsMatch) {
    const prodId = prodSlotsMatch[1];
    const prod = store.produtos.find((p) => p.id === prodId);
    if (!prod) {
      throwProblem('RECURSO_NAO_ENCONTRADO', 404, 'Produto não encontrado.');
    }
    if (prod.tipo !== 'COMBO') {
      throwProblem(
        'PRODUTO_NAO_E_COMBO',
        422,
        'Apenas produtos do tipo COMBO aceitam configuração de slots.'
      );
    }
    const rawSlots = (body.slots || []) as Array<{
      id?: string;
      nome: string;
      minSelecao: number;
      maxSelecao: number;
      obrigatorio?: boolean;
      ordem?: number;
      opcoes: Array<{
        produtoId: string;
        acrescimoCentavos?: number;
        padrao?: boolean;
        ordem?: number;
      }>;
    }>;

    const novosSlots: ComboSlot[] = rawSlots.map((s, idx) => {
      if (s.minSelecao < 0 || s.maxSelecao < 1 || s.maxSelecao < s.minSelecao) {
        throwProblem(
          'LIMITES_SELECAO_INVALIDOS',
          422,
          `Limites inválidos no slot '${s.nome}'.`
        );
      }
      const opcoes = s.opcoes.map((o, oIdx) => {
        const pFilho = store.produtos.find((p) => p.id === o.produtoId);
        if (!pFilho) {
          throwProblem('RECURSO_NAO_ENCONTRADO', 404, 'Produto do slot não encontrado.');
        }
        if (pFilho.tipo === 'COMBO') {
          throwProblem(
            'COMBO_ANINHADO',
            422,
            `O produto '${pFilho.nome}' também é um COMBO. Combos aninhados não são permitidos.`
          );
        }
        return {
          produtoId: pFilho.id,
          produtoNome: pFilho.nome,
          acrescimoCentavos: o.acrescimoCentavos ?? 0,
          padrao: o.padrao ?? oIdx === 0,
          ordem: o.ordem ?? oIdx + 1,
          disponivel: pFilho.disponivel,
        };
      });
      return {
        id: s.id || generateUuidV7(),
        nome: s.nome,
        minSelecao: s.minSelecao,
        maxSelecao: s.maxSelecao,
        obrigatorio: s.minSelecao >= 1,
        ordem: s.ordem ?? idx + 1,
        opcoes,
      };
    });

    prod.comboSlots = novosSlots;
    prod.versao += 1;
    recalcularCascataDisponibilidade(store);
    saveStore(store);
    return prod;
  }

  const prodGruposMatch = path.match(
    /^\/(?:catalogo\/)?produtos\/([^/]+)\/grupos-modificadores$/
  );
  if (method === 'PUT' && prodGruposMatch) {
    const prodId = prodGruposMatch[1];
    const prod = store.produtos.find((p) => p.id === prodId);
    if (!prod) {
      throwProblem('RECURSO_NAO_ENCONTRADO', 404, 'Produto não encontrado.');
    }
    const rawGrupos = (body.grupos || []) as Array<{
      grupoId: string;
      ordem?: number;
      minSelecaoOverride?: number | null;
      maxSelecaoOverride?: number | null;
    }>;

    prod.gruposModificadores = rawGrupos.map((v, idx) => {
      const grp = store.gruposModificadores.find((g) => g.id === v.grupoId);
      if (!grp) {
        throwProblem('RECURSO_NAO_ENCONTRADO', 404, 'Grupo modificador não encontrado.');
      }
      const minEfetiva = v.minSelecaoOverride ?? grp.minSelecao;
      const maxEfetiva = v.maxSelecaoOverride ?? grp.maxSelecao;
      if (minEfetiva < 0 || maxEfetiva < 1 || maxEfetiva < minEfetiva) {
        throwProblem(
          'LIMITES_SELECAO_INVALIDOS',
          422,
          'Limites de seleção inválidos para o grupo vinculado.'
        );
      }
      return {
        grupo: grp,
        ordem: v.ordem ?? idx + 1,
        minSelecaoOverride: v.minSelecaoOverride ?? null,
        maxSelecaoOverride: v.maxSelecaoOverride ?? null,
        minSelecaoEfetiva: minEfetiva,
        maxSelecaoEfetiva: maxEfetiva,
      };
    });
    prod.versao += 1;
    saveStore(store);
    return prod;
  }

  const prodInsumosMatch = path.match(/^\/(?:catalogo\/)?produtos\/([^/]+)\/insumos$/);
  if (method === 'PUT' && prodInsumosMatch) {
    const prodId = prodInsumosMatch[1];
    const prod = store.produtos.find((p) => p.id === prodId);
    if (!prod) {
      throwProblem('RECURSO_NAO_ENCONTRADO', 404, 'Produto não encontrado.');
    }
    prod.insumoIds = (body.insumoIds || []) as string[];
    prod.versao += 1;
    recalcularCascataDisponibilidade(store);
    saveStore(store);
    return prod;
  }

  const prodPrecoMatch = path.match(/^\/(?:catalogo\/)?produtos\/([^/]+)\/preco$/);
  if (method === 'PATCH' && prodPrecoMatch) {
    const prodId = prodPrecoMatch[1];
    const prod = store.produtos.find((p) => p.id === prodId);
    if (!prod) {
      throwProblem('RECURSO_NAO_ENCONTRADO', 404, 'Produto não encontrado.');
    }
    const novoPreco = Number(body.precoBaseCentavos);
    if (novoPreco < 0) {
      throwProblem('PRECO_INVALIDO', 422, 'O preço base não pode ser negativo.');
    }
    const hist: HistoricoPreco = {
      id: generateUuidV7(),
      entidadeTipo: 'PRODUTO',
      entidadeId: prod.id,
      tipoPrecoAnterior: 'FIXO',
      valorAnterior: prod.precoBaseCentavos,
      tipoPrecoNovo: 'FIXO',
      valorNovo: novoPreco,
      motivo: String(body.motivo || 'Reajuste de cardápio'),
      vigenteDesde: new Date().toISOString(),
      alteradoPor: store.usuarioAtual.nome,
    };
    store.historicoPrecos.unshift(hist);
    prod.precoBaseCentavos = novoPreco;
    prod.versao += 1;
    store.catalogoVersao += 1;
    saveStore(store);
    return prod;
  }

  const prodHistMatch = path.match(
    /^\/(?:catalogo\/)?produtos\/([^/]+)\/historico-precos$/
  );
  if (method === 'GET' && prodHistMatch) {
    const prodId = prodHistMatch[1];
    return store.historicoPrecos.filter((h) => h.entidadeId === prodId);
  }

  // 5. OPERAÇÃO DE CAIXA RIGOROSA (/caixas e /caixa)
  if (method === 'GET' && path === '/caixas') {
    return store.terminais.map((t) => {
      const aberta = store.sessoes.find(
        (s) => s.caixaId === t.id && s.status === 'ABERTA'
      );
      return {
        ...t,
        sessaoAbertaId: aberta?.id || null,
        operadorAtual: aberta?.operador || null,
      };
    });
  }

  if (method === 'POST' && path === '/caixas') {
    const nome = String(body.nome || '').trim();
    if (store.terminais.some((t) => t.nome.toLowerCase() === nome.toLowerCase())) {
      throwProblem('NOME_DUPLICADO', 409, 'Já existe um terminal de caixa com este nome.');
    }
    const novo: TerminalCaixa = {
      id: generateUuidV7(),
      nome,
      possuiGaveta: body.possuiGaveta ?? true,
      ativo: true,
      sessaoAbertaId: null,
      operadorAtual: null,
    };
    store.terminais.push(novo);
    saveStore(store);
    return novo;
  }

  if (method === 'POST' && (path === '/caixa/abertura' || path === '/caixa/sessoes')) {
    const caixaId = String(body.caixaId);
    const fundoTrocoCentavos = Number(body.fundoTrocoCentavos ?? 0);
    const terminal = store.terminais.find((t) => t.id === caixaId);
    if (!terminal) {
      throwProblem('RECURSO_NAO_ENCONTRADO', 404, 'Terminal de caixa não encontrado.');
    }

    const jaAbertoNoTerminal = store.sessoes.some(
      (s) => s.caixaId === caixaId && s.status === 'ABERTA'
    );
    if (jaAbertoNoTerminal) {
      throwProblem(
        'CAIXA_JA_ABERTO',
        409,
        'Este terminal de caixa já possui uma sessão ABERTA.'
      );
    }

    const agora = new Date().toISOString();
    const sessaoId = generateUuidV7();
    const operadorRef = {
      id: store.usuarioAtual.id,
      nome: store.usuarioAtual.nome,
    };

    const novaSessao: SessaoCaixaGerencial = {
      id: sessaoId,
      caixaId: terminal.id,
      caixaNome: terminal.nome,
      operador: operadorRef,
      status: 'ABERTA',
      abertaEm: agora,
      fechadaEm: null,
      conferidaEm: null,
      conferidaPor: null,
      fundoTrocoCentavos,
      esperadoDinheiroCentavos: null,
      declaradoDinheiroCentavos: null,
      diferencaDinheiroCentavos: null,
      possuiSinalizacoes: false,
      versao: 1,
    };
    store.sessoes.unshift(novaSessao);

    store.movimentacoes.push({
      id: generateUuidV7(),
      sessaoCaixaId: sessaoId,
      tipo: 'ABERTURA',
      natureza: 'ENTRADA',
      valorCentavos: fundoTrocoCentavos,
      motivo: 'Fundo de troco inicial na abertura de caixa',
      createdAt: agora,
      createdBy: operadorRef,
    });

    saveStore(store);

    const opResp: SessaoCaixaOperador = {
      id: sessaoId,
      caixaId: terminal.id,
      caixaNome: terminal.nome,
      operador: operadorRef,
      status: 'ABERTA',
      abertaEm: agora,
      dataAbertura: agora,
      fundoTrocoCentavos,
      quantidadePagamentos: 0,
      pedidosPosPagoEmAberto: store.pedidos.filter((p) => p.saldoDevedorCentavos > 0).length,
      versao: 1,
    };
    return opResp;
  }

  if (method === 'GET' && path === '/caixa/sessoes') {
    return store.sessoes;
  }

  // Sangria / Suprimento / Movimentação Manual
  const sangriaMatch = path.match(/^\/caixa\/sessoes\/([^/]+)\/sangrias$/);
  const suprimentoMatch = path.match(/^\/caixa\/sessoes\/([^/]+)\/suprimentos$/);
  if (
    method === 'POST' &&
    (path === '/caixa/movimentacoes' || sangriaMatch || suprimentoMatch)
  ) {
    const sessaoId =
      sangriaMatch?.[1] || suprimentoMatch?.[1] || String(body.sessaoCaixaId || '');
    const sessao =
      store.sessoes.find((s) => s.id === sessaoId) ||
      store.sessoes.find((s) => s.status === 'ABERTA');

    if (!sessao) {
      throwProblem(
        'SESSAO_CAIXA_INEXISTENTE',
        422,
        'Não há sessão de caixa aberta para registrar esta movimentação.'
      );
    }
    if (sessao.status !== 'ABERTA') {
      throwProblem('SESSAO_CAIXA_FECHADA', 422, 'A sessão de caixa informada já está fechada.');
    }

    const tipo = sangriaMatch
      ? 'SANGRIA'
      : suprimentoMatch
      ? 'SUPRIMENTO'
      : (body.tipo as 'SANGRIA' | 'SUPRIMENTO') || 'SANGRIA';
    const valorCentavos = Number(body.valorCentavos || 0);
    const motivo = String(body.motivo || '').trim();
    const pinAprovador = body.pinAprovador ? String(body.pinAprovador).trim() : '';

    let usuarioAprovadorId: string | null = null;
    let aprovadoPor: { id: string; nome: string } | null = null;
    let sinalizacao: string | null = null;

    if (tipo === 'SANGRIA') {
      if (valorCentavos > LIMITE_SANGRIA_SEM_PIN_CENTAVOS) {
        if (!pinAprovador) {
          throwProblem(
            'APROVACAO_NECESSARIA',
            403,
            `Sangria acima do limite da loja (R$ 500,00) exige o PIN de alçada de um GERENTE.`
          );
        }
        if (!PINS_GERENCIAIS_VALIDOS.has(pinAprovador)) {
          throwProblem(
            'PIN_APROVADOR_INVALIDO',
            403,
            'PIN informado não corresponde a nenhum GERENTE ou ADMIN ativo desta loja.'
          );
        }
        usuarioAprovadorId = '019325f0-8a00-7000-8009-000000000001';
        aprovadoPor = {
          id: usuarioAprovadorId,
          nome: 'Roberto Almeida (Gerente Geral)',
        };
      } else if (pinAprovador) {
        if (!PINS_GERENCIAIS_VALIDOS.has(pinAprovador)) {
          throwProblem(
            'PIN_APROVADOR_INVALIDO',
            403,
            'PIN informado não corresponde a nenhum GERENTE ou ADMIN ativo desta loja.'
          );
        }
        usuarioAprovadorId = '019325f0-8a00-7000-8009-000000000001';
        aprovadoPor = {
          id: usuarioAprovadorId,
          nome: 'Roberto Almeida (Gerente Geral)',
        };
      }

      const saldoDinheiroAtual = calcularSaldoDinheiroSessao(store, sessao.id);
      if (valorCentavos > saldoDinheiroAtual) {
        sinalizacao = 'SALDO_ESPERADO_NEGATIVO';
        sessao.possuiSinalizacoes = true;
      }
    }

    const novaMov: MovimentacaoGerencial = {
      id: generateUuidV7(),
      sessaoCaixaId: sessao.id,
      tipo,
      natureza: tipo === 'SANGRIA' ? 'SAIDA' : 'ENTRADA',
      valorCentavos,
      motivo,
      usuarioAprovadorId,
      aprovadoPor,
      sinalizacao,
      createdAt: new Date().toISOString(),
      createdBy: {
        id: store.usuarioAtual.id,
        nome: store.usuarioAtual.nome,
      },
    };
    store.movimentacoes.push(novaMov);
    saveStore(store);

    // Resposta do operador NUNCA inclui a sinalização de saldo negativo
    const opMov: MovimentacaoOperador = {
      id: novaMov.id,
      sessaoCaixaId: novaMov.sessaoCaixaId,
      tipo: novaMov.tipo,
      natureza: novaMov.natureza,
      valorCentavos: novaMov.valorCentavos,
      motivo: novaMov.motivo,
      usuarioAprovadorId: novaMov.usuarioAprovadorId,
      aprovadoPor: novaMov.aprovadoPor,
      createdAt: novaMov.createdAt,
      createdBy: novaMov.createdBy,
    };
    return opMov;
  }

  // Listar movimentações da sessão (Gerencial)
  const movsSessaoMatch = path.match(/^\/caixa\/sessoes\/([^/]+)\/movimentacoes$/);
  if (method === 'GET' && movsSessaoMatch) {
    const sessaoId = movsSessaoMatch[1];
    return store.movimentacoes.filter((m) => m.sessaoCaixaId === sessaoId);
  }

  // Estorno reverso imutável de movimentação
  const estornoMovMatch = path.match(/^\/caixa\/movimentacoes\/([^/]+)\/estorno$/);
  if (method === 'POST' && estornoMovMatch) {
    const movId = estornoMovMatch[1];
    const original = store.movimentacoes.find((m) => m.id === movId);
    if (!original) {
      throwProblem('RECURSO_NAO_ENCONTRADO', 404, 'Movimentação não encontrada.');
    }
    if (original.tipo !== 'SANGRIA' && original.tipo !== 'SUPRIMENTO') {
      throwProblem(
        'MOVIMENTACAO_NAO_ESTORNAVEL',
        422,
        'Apenas movimentações manuais de SANGRIA ou SUPRIMENTO podem ser estornadas diretamente.'
      );
    }
    if (store.movimentacoes.some((m) => m.movimentacaoEstornadaId === movId)) {
      throwProblem(
        'MOVIMENTACAO_JA_ESTORNADA',
        409,
        'Esta movimentação já possui lançamento reverso.'
      );
    }
    const reversa: MovimentacaoGerencial = {
      id: generateUuidV7(),
      sessaoCaixaId: original.sessaoCaixaId,
      tipo: 'ESTORNO_MOVIMENTACAO',
      natureza: original.natureza === 'ENTRADA' ? 'SAIDA' : 'ENTRADA',
      valorCentavos: original.valorCentavos,
      motivo: String(body.motivo || 'Estorno gerencial'),
      movimentacaoEstornadaId: original.id,
      usuarioAprovadorId: store.usuarioAtual.id,
      aprovadoPor: { id: store.usuarioAtual.id, nome: store.usuarioAtual.nome },
      createdAt: new Date().toISOString(),
      createdBy: { id: store.usuarioAtual.id, nome: store.usuarioAtual.nome },
    };
    store.movimentacoes.push(reversa);
    saveStore(store);
    return reversa;
  }

  // Fechamento Cego
  const fechamentoIdMatch = path.match(/^\/caixa\/sessoes\/([^/]+)\/fechamento$/);
  if (method === 'POST' && (path === '/caixa/fechamento' || fechamentoIdMatch)) {
    const req = body as FechamentoCegoRequest;
    const sessaoId = fechamentoIdMatch?.[1] || req.sessaoCaixaId;
    const sessao =
      store.sessoes.find((s) => s.id === sessaoId) ||
      store.sessoes.find((s) => s.status === 'ABERTA');

    if (!sessao) {
      throwProblem('SESSAO_CAIXA_INEXISTENTE', 422, 'Sessão de caixa não encontrada.');
    }
    if (sessao.status !== 'ABERTA') {
      throwProblem('SESSAO_CAIXA_FECHADA', 409, 'Esta sessão de caixa já foi fechada.');
    }

    const declaracoes = req.declaracoes || [];
    const declDinheiro = declaracoes.find((d) => d.metodo === 'DINHEIRO');
    if (!declDinheiro) {
      throwProblem(
        'DECLARACAO_DINHEIRO_OBRIGATORIA',
        422,
        'A declaração do valor contado em DINHEIRO é obrigatória no fechamento cego.'
      );
    }

    const esperadoDinheiro = calcularSaldoDinheiroSessao(store, sessao.id);
    const esperadosPorMetodo = calcularEsperadosPorMetodoSessao(
      store,
      sessao.id,
      esperadoDinheiro
    );

    const todosMetodos = new Set<MetodoPagamento>([
      'DINHEIRO',
      ...declaracoes.map((d) => d.metodo),
      ...(Object.keys(esperadosPorMetodo) as MetodoPagamento[]),
    ]);

    store.declaracoesPorSessao[sessao.id] = Array.from(todosMetodos).map((metodo) => {
      const decl = declaracoes.find((d) => d.metodo === metodo);
      return {
        metodo,
        declaradoCentavos: decl ? Number(decl.valorDeclaradoCentavos) : 0,
        esperadoCentavos: esperadosPorMetodo[metodo] ?? 0,
        quantidadeComprovantes: decl?.quantidadeComprovantes ?? null,
      };
    });

    store.observacoesSessao[sessao.id] = {
      ...store.observacoesSessao[sessao.id],
      operador: req.observacao || null,
    };

    const agora = new Date().toISOString();
    const dataCompacta = agora.slice(0, 10).replace(/-/g, '');
    const protocolo = `CX-${dataCompacta}-${sessao.id.slice(0, 8).toUpperCase()}`;

    sessao.status = 'FECHADA';
    sessao.fechadaEm = agora;
    sessao.esperadoDinheiroCentavos = esperadoDinheiro;
    sessao.declaradoDinheiroCentavos = Number(declDinheiro.valorDeclaradoCentavos);
    sessao.diferencaDinheiroCentavos =
      Number(declDinheiro.valorDeclaradoCentavos) - esperadoDinheiro;
    sessao.versao += 1;

    saveStore(store);

    const resp: FechamentoProtocoloResponse = {
      sessaoId: sessao.id,
      protocolo,
      status: 'FECHADA',
      fechadaEm: agora,
      alertas: [],
    };
    return resp;
  }

  // Conferência Gerencial
  const confMatch = path.match(/^\/caixa\/sessoes\/([^/]+)\/conferencia$/);
  const conferirMatch = path.match(/^\/caixa\/sessoes\/([^/]+)\/conferir$/);
  if ((method === 'GET' && confMatch) || (method === 'POST' && conferirMatch)) {
    const sessaoId = (confMatch?.[1] || conferirMatch?.[1])!;
    const sessao = store.sessoes.find((s) => s.id === sessaoId);
    if (!sessao) {
      throwProblem('RECURSO_NAO_ENCONTRADO', 404, 'Sessão de caixa não encontrada.');
    }
    if (sessao.status === 'ABERTA') {
      throwProblem(
        'SESSAO_NAO_FECHADA',
        422,
        'A conferência gerencial só está disponível após o fechamento cego.'
      );
    }

    if (method === 'POST' && conferirMatch) {
      sessao.status = 'CONFERIDA';
      sessao.conferidaEm = new Date().toISOString();
      sessao.conferidaPor = {
        id: store.usuarioAtual.id,
        nome: store.usuarioAtual.nome,
      };
      sessao.versao += 1;
      store.observacoesSessao[sessao.id] = {
        ...store.observacoesSessao[sessao.id],
        conferencia: body.observacao || null,
      };
      saveStore(store);
    }

    const movs = store.movimentacoes.filter((m) => m.sessaoCaixaId === sessao.id);
    const composicao = calcularComposicaoDinheiro(movs);
    const decls = store.declaracoesPorSessao[sessao.id] || [];

    const porMetodo: ConferenciaMetodoItem[] = decls.map((d) => {
      const diff = d.declaradoCentavos - d.esperadoCentavos;
      const situacao: SituacaoConferencia =
        diff === 0 ? 'OK' : diff < 0 ? 'QUEBRA' : 'SOBRA';
      return {
        metodo: d.metodo,
        esperadoCentavos: d.esperadoCentavos,
        declaradoCentavos: d.declaradoCentavos,
        diferencaCentavos: diff,
        situacao,
        quantidadeComprovantesDeclarada: d.quantidadeComprovantes ?? null,
        quantidadePagamentos: 0,
      };
    });

    const resp: ConferenciaResponse = {
      sessaoId: sessao.id,
      status: sessao.status,
      operador: sessao.operador,
      abertaEm: sessao.abertaEm,
      fechadaEm: sessao.fechadaEm,
      porMetodo,
      composicaoDinheiro: composicao,
      sinalizacoes: movs.filter((m) => Boolean(m.sinalizacao)),
      observacaoOperador: store.observacoesSessao[sessao.id]?.operador || null,
      observacaoConferencia: store.observacoesSessao[sessao.id]?.conferencia || null,
    };
    return resp;
  }

  // 6. PEDIDOS, PAGAMENTOS & KDS
  if (method === 'GET' && path === '/pedidos') {
    try {
      const bridgeRes = await fetch('/api/v1/bridge/orders');
      if (bridgeRes.ok) {
        const bridgeOrders = (await bridgeRes.json()) as PedidoResponse[];
        let changed = false;
        for (const bo of bridgeOrders) {
          const localPed = store.pedidos.find((p) => p.id === bo.id);
          if (localPed) {
            if (localPed.status !== bo.status || localPed.versao !== bo.versao) {
              localPed.status = bo.status;
              localPed.versao = bo.versao;
              localPed.preparoIniciadoEm = bo.preparoIniciadoEm;
              localPed.prontoEm = bo.prontoEm;
              localPed.entregueEm = bo.entregueEm;
              localPed.updatedAt = bo.updatedAt || localPed.updatedAt;
              changed = true;
            }
          } else {
            store.pedidos.unshift(bo);
            changed = true;
          }
        }
        if (changed) {
          saveStore(store);
        }
      }
    } catch {
      // ignora se bridge offline
    }
    return store.pedidos;
  }

  if (method === 'POST' && path === '/pedidos') {
    const req = body as CriarPedidoRequest;
    const agora = new Date().toISOString();
    const pedidoId = generateUuidV7();

    let subtotalCentavos = 0;
    const itensResp: ItemPedidoResponse[] = [];

    for (const itemReq of req.itens) {
      const prod = store.produtos.find((p) => p.id === itemReq.produtoId);
      if (!prod || !prod.disponivel) {
        throwProblem(
          'ITENS_INDISPONIVEIS',
          409,
          `O produto selecionado está esgotado no momento.`,
          { itensIndisponiveis: [itemReq.produtoId] }
        );
      }

      if (prod.tipo === 'SIMPLES') {
        const modsResp: ItemPedidoModificadorResponse[] = [];
        let adicionalUnitario = 0;

        for (const ms of itemReq.modificadores || []) {
          const mod = store.gruposModificadores
            .flatMap((g) => g.modificadores.map((m) => ({ grupo: g, mod: m })))
            .find((x) => x.mod.id === ms.modificadorId);

          if (!mod || !mod.mod.disponivel) {
            throwProblem(
              'ITENS_INDISPONIVEIS',
              409,
              `O adicional selecionado está esgotado.`,
              { itensIndisponiveis: [ms.modificadorId] }
            );
          }

          let valorCalc = 0;
          if (mod.mod.tipoPreco === 'FIXO') {
            valorCalc = mod.mod.valorPreco * ms.quantidade;
          } else if (mod.mod.tipoPreco === 'PERCENTUAL') {
            valorCalc =
              calcPercentualHalfEven(prod.precoBaseCentavos, mod.mod.valorPreco) *
              ms.quantidade;
          }
          adicionalUnitario += valorCalc;

          modsResp.push({
            modificadorId: mod.mod.id,
            grupoNome: mod.grupo.nome,
            modificadorNome: mod.mod.nome,
            tipoPreco: mod.mod.tipoPreco,
            valorPreco: mod.mod.valorPreco,
            quantidade: ms.quantidade,
            valorCalculadoCentavos: valorCalc,
          });
        }

        const unitarioFinal = prod.precoBaseCentavos + adicionalUnitario;
        const totalItem = unitarioFinal * itemReq.quantidade;
        subtotalCentavos += totalItem;

        itensResp.push({
          id: generateUuidV7(),
          produtoId: prod.id,
          produtoNome: prod.nome,
          quantidade: itemReq.quantidade,
          precoBaseCentavos: prod.precoBaseCentavos,
          precoUnitarioFinalCentavos: unitarioFinal,
          totalCentavos: totalItem,
          estacaoPreparo: prod.estacaoPreparoEfetiva,
          observacao: itemReq.observacao || null,
          modificadores: modsResp,
          filhos: [],
        });
      } else {
        // Produto COMBO
        const filhosResp: ItemPedidoResponse[] = [];
        let somaAcrescimosFilhos = 0;

        for (const sel of itemReq.selecoesCombo || []) {
          const slot = prod.comboSlots.find((s) => s.id === sel.slotId);
          const opcao = slot?.opcoes.find((o) => o.produtoId === sel.produtoId);
          const prodFilho = store.produtos.find((p) => p.id === sel.produtoId);
          if (!slot || !opcao || !prodFilho) {
            throwProblem('COMBO_SLOT_INVALIDO', 422, 'Seleção de combo inválida.');
          }
          somaAcrescimosFilhos += opcao.acrescimoCentavos;
          filhosResp.push({
            id: generateUuidV7(),
            produtoId: prodFilho.id,
            produtoNome: prodFilho.nome,
            quantidade: itemReq.quantidade,
            precoBaseCentavos: opcao.acrescimoCentavos,
            precoUnitarioFinalCentavos: opcao.acrescimoCentavos,
            totalCentavos: opcao.acrescimoCentavos * itemReq.quantidade,
            estacaoPreparo: prodFilho.estacaoPreparoEfetiva,
            observacao: sel.observacao || null,
            comboSlotNome: slot.nome,
            modificadores: [],
            filhos: [],
          });
        }

        const unitarioCombo = prod.precoBaseCentavos + somaAcrescimosFilhos;
        const totalCombo = unitarioCombo * itemReq.quantidade;
        subtotalCentavos += totalCombo;

        itensResp.push({
          id: generateUuidV7(),
          produtoId: prod.id,
          produtoNome: prod.nome,
          quantidade: itemReq.quantidade,
          precoBaseCentavos: prod.precoBaseCentavos,
          precoUnitarioFinalCentavos: unitarioCombo,
          totalCentavos: totalCombo,
          estacaoPreparo: 'NENHUMA',
          observacao: itemReq.observacao || null,
          modificadores: [],
          filhos: filhosResp,
        });
      }
    }

    const desconto = Math.max(0, Number(req.descontoCentavos || 0));
    const totalCalculado = subtotalCentavos - desconto;
    if (totalCalculado < 0) {
      throwProblem('PRECO_INVALIDO', 422, 'O desconto não pode superar o subtotal.');
    }

    const numeroDiario =
      store.pedidos.reduce((acc, p) => Math.max(acc, p.numeroDiario), 100) + 1;

    const sessaoAberta =
      store.sessoes.find((s) => s.id === req.sessaoCaixaId && s.status === 'ABERTA') ||
      store.sessoes.find((s) => s.status === 'ABERTA');

    const pagamentos: PagamentoResponse[] = [];
    let totalPago = 0;
    let statusInicial: StatusPedido =
      req.modalidadePagamento === 'POS_PAGO' ? 'CONFIRMADO' : 'AGUARDANDO_PAGAMENTO';

    if (req.pagamentoInicial) {
      if (!sessaoAberta) {
        throwProblem(
          'SESSAO_CAIXA_INEXISTENTE',
          422,
          'Abra uma sessão de caixa antes de registrar pagamentos no PDV.'
        );
      }
      const pagValor = Number(req.pagamentoInicial.valorCentavos);
      const pagRecebido =
        req.pagamentoInicial.metodo === 'DINHEIRO'
          ? Number(req.pagamentoInicial.valorRecebidoCentavos ?? pagValor)
          : null;

      if (req.pagamentoInicial.metodo === 'DINHEIRO' && (pagRecebido ?? 0) < pagValor) {
        throwProblem(
          'RECEBIDO_INSUFICIENTE',
          422,
          'O valor recebido em dinheiro é menor que o valor do pagamento.'
        );
      }

      const troco =
        req.pagamentoInicial.metodo === 'DINHEIRO' && pagRecebido !== null
          ? pagRecebido - pagValor
          : null;

      const pagId = generateUuidV7();
      totalPago = pagValor;
      if (totalPago >= totalCalculado) {
        statusInicial = 'CONFIRMADO';
      }

      pagamentos.push({
        id: pagId,
        pedidoId,
        sessaoCaixaId: sessaoAberta.id,
        metodo: req.pagamentoInicial.metodo,
        valorCentavos: pagValor,
        valorRecebidoCentavos: pagRecebido,
        trocoCentavos: troco,
        status: 'CONFIRMADO',
        pedidoStatusApos: statusInicial,
        saldoDevedorAposCentavos: Math.max(0, totalCalculado - totalPago),
        createdAt: agora,
      });

      if (req.pagamentoInicial.metodo === 'DINHEIRO') {
        store.movimentacoes.push({
          id: generateUuidV7(),
          sessaoCaixaId: sessaoAberta.id,
          tipo: 'VENDA_DINHEIRO',
          natureza: 'ENTRADA',
          valorCentavos: pagValor,
          motivo: `Recebimento em espécie ref. pedido #${numeroDiario}`,
          pagamentoId: pagId,
          createdAt: agora,
          createdBy: { id: store.usuarioAtual.id, nome: store.usuarioAtual.nome },
        });
      }
    }

    const novoPedido: PedidoResponse = {
      id: pedidoId,
      sessaoCaixaId: sessaoAberta?.id || null,
      clienteId: null,
      numeroDiario,
      dataOperacional: agora.slice(0, 10),
      origem: req.origem || 'MASTER_BALCAO',
      tipoAtendimento: req.tipoAtendimento || 'BALCAO',
      modalidadePagamento: req.modalidadePagamento || 'PRE_PAGO',
      identificadorCliente: req.identificadorCliente || 'Cliente Balcão',
      mesa: req.mesa || null,
      observacao: req.observacao || null,
      status: statusInicial,
      versao: 1,
      itens: itensResp,
      subtotalCentavos,
      descontoCentavos: desconto,
      totalCentavos: totalCalculado,
      valorTotalCentavos: totalCalculado,
      totalPagoCentavos: totalPago,
      saldoDevedorCentavos: Math.max(0, totalCalculado - totalPago),
      pagamentos,
      confirmadoEm: statusInicial === 'CONFIRMADO' ? agora : null,
      createdAt: agora,
      updatedAt: agora,
    };

    store.pedidos.unshift(novoPedido);
    store.seqEventoKds += 1;
    saveStore(store);

    if (statusInicial === 'CONFIRMADO') {
      const evKds: PedidoCozinhaEventoDto = {
        seq: store.seqEventoKds,
        tipo: 'PEDIDO_CONFIRMADO',
        pedidoId: novoPedido.id,
        versao: novoPedido.versao,
        status: novoPedido.status,
        ocorridoEm: agora,
      };
      emitSimulatedStompMessage(`/topic/lojas/${LOJA_MATRIZ_ID}/kds`, evKds);
      try {
        await fetch('/api/v1/bridge/orders', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            pedidoMaster: novoPedido,
            tipoEvento: 'PEDIDO_CONFIRMADO',
          }),
        });
      } catch {
        // ignora se bridge offline
      }
    }

    return novoPedido;
  }

  // Pagamento avulso de pedido pós-pago
  if (method === 'POST' && path === '/pagamentos') {
    const req = body as PagamentoRequest;
    const pedido = store.pedidos.find((p) => p.id === req.pedidoId);
    if (!pedido) {
      throwProblem('RECURSO_NAO_ENCONTRADO', 404, 'Pedido não encontrado.');
    }
    const sessaoAberta =
      store.sessoes.find((s) => s.id === req.sessaoCaixaId && s.status === 'ABERTA') ||
      store.sessoes.find((s) => s.status === 'ABERTA');
    if (!sessaoAberta) {
      throwProblem(
        'SESSAO_CAIXA_INEXISTENTE',
        422,
        'Abra uma sessão de caixa antes de registrar pagamentos.'
      );
    }

    const valor = Number(req.valorCentavos);
    if (valor > pedido.saldoDevedorCentavos) {
      throwProblem(
        'VALOR_EXCEDE_SALDO_DEVEDOR',
        422,
        'O valor informado excede o saldo devedor do pedido.'
      );
    }
    const recebido =
      req.metodo === 'DINHEIRO' ? Number(req.valorRecebidoCentavos ?? valor) : null;
    if (req.metodo === 'DINHEIRO' && (recebido ?? 0) < valor) {
      throwProblem('RECEBIDO_INSUFICIENTE', 422, 'Valor recebido insuficiente.');
    }
    const troco =
      req.metodo === 'DINHEIRO' && recebido !== null ? recebido - valor : null;

    const agora = new Date().toISOString();
    const pagId = generateUuidV7();
    pedido.totalPagoCentavos += valor;
    pedido.saldoDevedorCentavos = Math.max(
      0,
      pedido.valorTotalCentavos - pedido.totalPagoCentavos
    );
    if (
      pedido.saldoDevedorCentavos === 0 &&
      (pedido.status === 'CRIADO' || pedido.status === 'AGUARDANDO_PAGAMENTO')
    ) {
      pedido.status = 'CONFIRMADO';
      pedido.confirmadoEm = agora;
    }
    pedido.versao += 1;
    pedido.updatedAt = agora;

    const pagResp: PagamentoResponse = {
      id: pagId,
      pedidoId: pedido.id,
      sessaoCaixaId: sessaoAberta.id,
      metodo: req.metodo,
      valorCentavos: valor,
      valorRecebidoCentavos: recebido,
      trocoCentavos: troco,
      status: 'CONFIRMADO',
      pedidoStatusApos: pedido.status,
      saldoDevedorAposCentavos: pedido.saldoDevedorCentavos,
      createdAt: agora,
    };
    pedido.pagamentos.push(pagResp);

    if (req.metodo === 'DINHEIRO') {
      store.movimentacoes.push({
        id: generateUuidV7(),
        sessaoCaixaId: sessaoAberta.id,
        tipo: 'VENDA_DINHEIRO',
        natureza: 'ENTRADA',
        valorCentavos: valor,
        motivo: `Recebimento em espécie ref. pedido #${pedido.numeroDiario}`,
        pagamentoId: pagId,
        createdAt: agora,
        createdBy: { id: store.usuarioAtual.id, nome: store.usuarioAtual.nome },
      });
    }

    saveStore(store);
    return pagResp;
  }

  // Transições de Pedido (Confirmar, Iniciar Preparo, Marcar Pronto, Entregar, Cancelar)
  const pedidoAcaoMatch = path.match(
    /^\/(?:cozinha\/)?pedidos\/([^/]+)\/(confirmar|iniciar-preparo|marcar-pronto|entregar|cancelar|status)$/
  );
  if ((method === 'POST' || method === 'PATCH') && pedidoAcaoMatch) {
    const pedidoId = pedidoAcaoMatch[1];
    const acao = pedidoAcaoMatch[2];
    const pedido = store.pedidos.find((p) => p.id === pedidoId);
    if (!pedido) {
      throwProblem('RECURSO_NAO_ENCONTRADO', 404, 'Pedido não encontrado.');
    }

    if (body.versao !== undefined && Number(body.versao) !== pedido.versao) {
      throwProblem(
        'TRANSICAO_CONCORRENTE',
        409,
        `O pedido #${pedido.numeroDiario} já mudou para ${pedido.status} (versão ${pedido.versao}).`,
        {
          estadoAtual: { status: pedido.status, versao: pedido.versao },
        }
      );
    }

    const agora = new Date().toISOString();

    if (acao === 'cancelar') {
      const exigeGerente =
        pedido.status !== 'CRIADO' &&
        pedido.status !== 'AGUARDANDO_PAGAMENTO' &&
        pedido.status !== 'CONFIRMADO';

      if (exigeGerente) {
        const pin = body.pinAprovador ? String(body.pinAprovador).trim() : '';
        if (!pin) {
          throwProblem(
            'CANCELAMENTO_REQUER_GERENTE',
            403,
            `O pedido #${pedido.numeroDiario} já está em ${pedido.status}; o cancelamento exige o PIN de alçada de um GERENTE.`
          );
        }
        if (!PINS_GERENCIAIS_VALIDOS.has(pin)) {
          throwProblem(
            'PIN_APROVADOR_INVALIDO',
            403,
            'PIN informado não corresponde a nenhum GERENTE ou ADMIN ativo desta loja.'
          );
        }
      }

      const tinhaPagamento = pedido.totalPagoCentavos > 0;
      pedido.status = tinhaPagamento ? 'ESTORNADO' : 'CANCELADO';
      pedido.motivoCancelamento = String(body.motivo || 'Cancelado na operação');
      pedido.versao += 1;
      pedido.updatedAt = agora;
      saveStore(store);

      emitSimulatedStompMessage(`/topic/lojas/${LOJA_MATRIZ_ID}/kds`, {
        seq: ++store.seqEventoKds,
        tipo: 'PEDIDO_CANCELADO',
        pedidoId: pedido.id,
        versao: pedido.versao,
        status: pedido.status,
        ocorridoEm: agora,
        motivo: pedido.motivoCancelamento,
      } satisfies PedidoCozinhaEventoDto);

      return pedido;
    }

    const novoStatus: StatusPedido =
      acao === 'confirmar'
        ? 'CONFIRMADO'
        : acao === 'iniciar-preparo'
        ? 'EM_PREPARO'
        : acao === 'marcar-pronto'
        ? 'PRONTO'
        : acao === 'entregar'
        ? 'ENTREGUE'
        : (body.status as StatusPedido);

    pedido.status = novoStatus;
    if (novoStatus === 'CONFIRMADO') pedido.confirmadoEm = agora;
    if (novoStatus === 'EM_PREPARO') pedido.preparoIniciadoEm = agora;
    if (novoStatus === 'PRONTO') pedido.prontoEm = agora;
    if (novoStatus === 'ENTREGUE') pedido.entregueEm = agora;
    pedido.versao += 1;
    pedido.updatedAt = agora;
    saveStore(store);

    emitSimulatedStompMessage(`/topic/lojas/${LOJA_MATRIZ_ID}/kds`, {
      seq: ++store.seqEventoKds,
      tipo: `PEDIDO_${novoStatus}`,
      pedidoId: pedido.id,
      versao: pedido.versao,
      status: pedido.status,
      ocorridoEm: agora,
    } satisfies PedidoCozinhaEventoDto);

    try {
      await fetch('/api/v1/bridge/orders', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          pedidoMaster: pedido,
          tipoEvento: `PEDIDO_${novoStatus}`,
        }),
      });
    } catch {
      // ignora se bridge offline
    }

    if (novoStatus === 'PRONTO') {
      emitSimulatedStompMessage(`/topic/lojas/${LOJA_MATRIZ_ID}/pedidos/prontos`, {
        pedidoId: pedido.id,
        numeroDiario: pedido.numeroDiario,
        identificadorCliente: pedido.identificadorCliente,
        mesa: pedido.mesa,
      } satisfies PedidoProntoEventoDto);
    }

    return pedido;
  }

  return {};
}

function calcularSaldoDinheiroSessao(
  store: SimulationStore,
  sessaoId: string
): number {
  return store.movimentacoes
    .filter((m) => m.sessaoCaixaId === sessaoId)
    .reduce(
      (acc, m) =>
        acc + (m.natureza === 'ENTRADA' ? m.valorCentavos : -m.valorCentavos),
      0
    );
}

function calcularEsperadosPorMetodoSessao(
  store: SimulationStore,
  sessaoId: string,
  esperadoDinheiro: number
): Record<MetodoPagamento, number> {
  const mapa: Record<MetodoPagamento, number> = {
    DINHEIRO: esperadoDinheiro,
    CARTAO_CREDITO: 0,
    CARTAO_DEBITO: 0,
    PIX: 0,
    VOUCHER: 0,
  };

  for (const ped of store.pedidos) {
    for (const pag of ped.pagamentos) {
      if (
        pag.sessaoCaixaId === sessaoId &&
        pag.status === 'CONFIRMADO' &&
        pag.metodo !== 'DINHEIRO'
      ) {
        mapa[pag.metodo] = (mapa[pag.metodo] || 0) + pag.valorCentavos;
      }
    }
  }
  return mapa;
}

function calcularComposicaoDinheiro(movs: MovimentacaoGerencial[]) {
  let aberturaCentavos = 0;
  let vendasCentavos = 0;
  let suprimentosCentavos = 0;
  let sangriasCentavos = 0;
  let estornosCentavos = 0;

  for (const m of movs) {
    if (m.tipo === 'ABERTURA') aberturaCentavos += m.valorCentavos;
    else if (m.tipo === 'VENDA' || m.tipo === 'VENDA_DINHEIRO')
      vendasCentavos += m.valorCentavos;
    else if (m.tipo === 'SUPRIMENTO') suprimentosCentavos += m.valorCentavos;
    else if (m.tipo === 'SANGRIA') sangriasCentavos += m.valorCentavos;
    else if (
      m.tipo === 'ESTORNO' ||
      m.tipo === 'ESTORNO_VENDA_DINHEIRO' ||
      m.tipo === 'ESTORNO_MOVIMENTACAO'
    ) {
      estornosCentavos +=
        m.natureza === 'SAIDA' ? m.valorCentavos : -m.valorCentavos;
    }
  }

  const esperadoCentavos =
    aberturaCentavos +
    vendasCentavos +
    suprimentosCentavos -
    sangriasCentavos -
    estornosCentavos;

  return {
    aberturaCentavos,
    vendasCentavos,
    suprimentosCentavos,
    sangriasCentavos,
    estornosCentavos,
    esperadoCentavos,
  };
}
