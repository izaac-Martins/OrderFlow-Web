// ═══════════════════════════════════════════════════════════════════════════
// OrderFlow KDS — Contratos de Dados (Estritamente SEM valores financeiros)
// Alinhado com openapi.yaml e PedidoDtos.java do Backend
// ═══════════════════════════════════════════════════════════════════════════

export type EstacaoPreparo = 'BAR' | 'COZINHA' | 'NENHUMA';
export type FiltroEstacaoKds = 'TODAS' | 'BAR' | 'COZINHA';

export type TipoAtendimento = 'BALCAO' | 'MESA' | 'DELIVERY';

export type StatusPedidoKds =
  | 'RECEBIDO'
  | 'CONFIRMADO'
  | 'EM_PREPARO'
  | 'PRONTO'
  | 'ENTREGUE'
  | 'CANCELADO'
  | 'ESTORNADO';

export interface ItemCozinhaDto {
  itemId: string;
  produtoNome: string;
  quantidade: number;
  estacao: EstacaoPreparo;
  modificadores: string[];
  observacao?: string | null;
  comboNome?: string | null;
}

export interface PedidoCozinhaDto {
  pedidoId: string;
  numeroDiario: number;
  identificadorCliente?: string | null;
  mesa?: string | null;
  tipoAtendimento: TipoAtendimento;
  status: StatusPedidoKds;
  versao: number;
  observacao?: string | null;
  confirmadoEm: string;
  preparoIniciadoEm?: string | null;
  prontoEm?: string | null;
  itens: ItemCozinhaDto[];
}

export interface CozinhaSnapshotResponse {
  seqAtual: number;
  geradoEm: string;
  pedidos: PedidoCozinhaDto[];
}

export type TipoEventoKds =
  | 'PEDIDO_CONFIRMADO'
  | 'PREPARO_INICIADO'
  | 'PEDIDO_PRONTO'
  | 'PEDIDO_REFEITO'
  | 'PEDIDO_ENTREGUE'
  | 'PEDIDO_CANCELADO';

export interface PedidoCozinhaEventoDto {
  seq: number;
  tipo: TipoEventoKds | string;
  pedidoId: string;
  versao: number;
  status: StatusPedidoKds;
  ocorridoEm: string;
  motivo?: string | null;
  pedido?: PedidoCozinhaDto | null;
}

export interface PedidoProntoEventoDto {
  pedidoId: string;
  numeroDiario: number;
  identificadorCliente?: string | null;
  mesa?: string | null;
}

export interface Insumo {
  id: string;
  nome: string;
  unidade: string;
  unidadeMedida: string;
  quantidadeAtual: number;
  estoqueMinimo: number;
  disponivel: boolean;
  motivoIndisponibilidade?: string | null;
  versao: number;
}

export interface ItemAfetadoResumo {
  id: string;
  nome: string;
  disponivel: boolean;
}

export interface InsumoDisponibilidadeResponse {
  insumo: Insumo;
  catalogoVersao: number;
  produtosAfetados: ItemAfetadoResumo[];
  modificadoresAfetados: ItemAfetadoResumo[];
}

export interface ProblemDetail {
  type: string;
  title: string;
  status: number;
  code: string;
  detail: string;
  estadoAtual?: {
    status: StatusPedidoKds;
    versao: number;
  };
}
