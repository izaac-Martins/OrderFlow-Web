// ═══════════════════════════════════════════════════════════════════════════
// OrderFlow — Tipos & Contratos da API (Alinhados com openapi.yaml e Backend)
// ═══════════════════════════════════════════════════════════════════════════

export type UserRole =
  | 'ADMIN'
  | 'GERENTE'
  | 'OPERADOR_CAIXA'
  | 'ATENDENTE'
  | 'COZINHA';

export type EstacaoPreparo = 'BAR' | 'COZINHA' | 'NENHUMA';
export type TipoProduto = 'SIMPLES' | 'COMBO';
export type TipoPreco = 'FIXO' | 'PERCENTUAL' | 'NENHUM';

export type StatusSessaoCaixa = 'ABERTA' | 'FECHADA' | 'CONFERIDA' | 'AGUARDANDO_CONFERENCIA';
export type TipoMovimentacao =
  | 'ABERTURA'
  | 'SANGRIA'
  | 'SUPRIMENTO'
  | 'VENDA'
  | 'VENDA_DINHEIRO'
  | 'ESTORNO'
  | 'ESTORNO_VENDA_DINHEIRO'
  | 'ESTORNO_MOVIMENTACAO';
export type NaturezaMovimentacao = 'ENTRADA' | 'SAIDA';
export type MetodoPagamento =
  | 'DINHEIRO'
  | 'CARTAO_CREDITO'
  | 'CARTAO_DEBITO'
  | 'PIX'
  | 'VOUCHER';
export type StatusPagamento = 'CONFIRMADO' | 'ESTORNADO';
export type SituacaoConferencia = 'OK' | 'QUEBRA' | 'SOBRA';

export type OrigemPedido = 'APP_ATENDENTE' | 'MASTER_BALCAO';
export type TipoAtendimento = 'BALCAO' | 'MESA' | 'DELIVERY';
export type ModalidadePagamento = 'PRE_PAGO' | 'POS_PAGO';
export type StatusPedido =
  | 'CRIADO'
  | 'AGUARDANDO_PAGAMENTO'
  | 'CONFIRMADO'
  | 'EM_PREPARO'
  | 'PRONTO'
  | 'ENTREGUE'
  | 'CANCELADO'
  | 'ESTORNADO';

export type CodigoErro =
  | 'NAO_AUTENTICADO'
  | 'TOKEN_EXPIRADO'
  | 'CREDENCIAIS_INVALIDAS'
  | 'CONTA_BLOQUEADA'
  | 'REFRESH_TOKEN_INVALIDO'
  | 'ACESSO_NEGADO'
  | 'APROVACAO_NECESSARIA'
  | 'PIN_APROVADOR_INVALIDO'
  | 'CANCELAMENTO_REQUER_GERENTE'
  | 'SESSAO_DE_OUTRO_OPERADOR'
  | 'RECURSO_NAO_ENCONTRADO'
  | 'VERSAO_CONFLITANTE'
  | 'TRANSICAO_CONCORRENTE'
  | 'IDEMPOTENCIA_CONFLITO'
  | 'SKU_DUPLICADO'
  | 'SLUG_DUPLICADO'
  | 'NOME_DUPLICADO'
  | 'EMAIL_DUPLICADO'
  | 'CATEGORIA_COM_PRODUTOS_ATIVOS'
  | 'CAIXA_JA_ABERTO'
  | 'OPERADOR_COM_SESSAO'
  | 'PAGAMENTO_JA_ESTORNADO'
  | 'MOVIMENTACAO_JA_ESTORNADA'
  | 'EVENTOS_EXPIRADOS'
  | 'VALIDACAO_CAMPOS'
  | 'VERSAO_AUSENTE'
  | 'IDEMPOTENCY_KEY_AUSENTE'
  | 'CATEGORIA_PROFUNDIDADE_EXCEDIDA'
  | 'CICLO_CATEGORIA'
  | 'PRECO_INVALIDO'
  | 'LIMITE_DESCONTO_PERCENTUAL'
  | 'LIMITES_SELECAO_INVALIDOS'
  | 'COMBO_ANINHADO'
  | 'PRODUTO_NAO_E_COMBO'
  | 'ITEM_INDISPONIVEL'
  | 'ITENS_INDISPONIVEIS'
  | 'MODIFICADOR_OBRIGATORIO'
  | 'MODIFICADOR_LIMITE'
  | 'MODIFICADOR_NAO_PERTENCE_PRODUTO'
  | 'MODIFICADOR_REPETICAO_NAO_PERMITIDA'
  | 'COMBO_SLOT_INVALIDO'
  | 'PRECO_DESATUALIZADO'
  | 'TRANSICAO_INVALIDA'
  | 'PEDIDO_NAO_QUITADO'
  | 'VALOR_EXCEDE_SALDO_DEVEDOR'
  | 'RECEBIDO_INSUFICIENTE'
  | 'TROCO_APENAS_DINHEIRO'
  | 'SESSAO_CAIXA_FECHADA'
  | 'SESSAO_CAIXA_INEXISTENTE'
  | 'DECLARACAO_DINHEIRO_OBRIGATORIA'
  | 'METODO_DECLARADO_DUPLICADO'
  | 'SESSAO_NAO_FECHADA'
  | 'MOVIMENTACAO_NAO_ESTORNAVEL'
  | 'IMPRESSORA_INDISPONIVEL'
  | 'ERRO_INTERNO';

// ─────────────────────────── RFC 7807 Problem Details ───────────────────────────

export interface CampoErro {
  campo: string;
  mensagem: string;
  valorRejeitado?: unknown;
}

export interface EstadoAtualConflito {
  status: string;
  versao: number;
}

export interface ProblemDetail {
  type: string;
  title: string;
  status: number;
  code: CodigoErro;
  detail: string;
  instance?: string;
  traceId?: string;
  erros?: CampoErro[];
  estadoAtual?: EstadoAtualConflito;
  itensIndisponiveis?: string[];
}

// ─────────────────────────── Autenticação & Usuários ───────────────────────────

export interface UsuarioAutenticado {
  id: string;
  lojaId: string;
  codigoLoja?: string;
  nomeLoja?: string;
  nome: string;
  email: string;
  role: UserRole;
  permissions: string[];
  possuiPinAlcada?: boolean;
}

export interface LoginRequest {
  email: string;
  senha: string;
  lojaId?: string;
  codigoLoja?: string;
  dispositivoId?: string;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  usuario: UsuarioAutenticado;
}

export interface ValidarPinRequest {
  pin: string;
  operacao?: string;
}

export interface ValidarPinResponse {
  valido: boolean;
  aprovadorId: string;
  aprovadorNome: string;
  role: UserRole;
  validadoEm: string;
}

// ─────────────────────────── Catálogo & Disponibilidade ───────────────────────────

export interface Categoria {
  id: string;
  parentId: string | null;
  categoriaPaiId: string | null;
  nome: string;
  slug: string;
  ordem: number;
  ordemExibicao: number;
  ativo: boolean;
  arquivada: boolean;
  estacaoPreparoPadrao: EstacaoPreparo;
  versao: number;
  subcategorias?: Categoria[];
}

export interface CategoriaRequest {
  parentId?: string | null;
  nome: string;
  slug: string;
  ordemExibicao: number;
  ativo: boolean;
  estacaoPreparoPadrao: EstacaoPreparo;
  versao?: number;
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

export interface InsumoRequest {
  nome: string;
  unidadeMedida: string;
  quantidadeAtual: number;
  estoqueMinimo: number;
  disponivel: boolean;
  motivoIndisponibilidade?: string | null;
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

export interface OpcaoModificador {
  id: string;
  grupoId: string;
  nome: string;
  tipoPreco: TipoPreco;
  valorPreco: number;
  precoAdicionalCentavos: number;
  padrao: boolean;
  maxQuantidade: number;
  ordem: number;
  ativo: boolean;
  pausado: boolean;
  disponivel: boolean;
  insumoIds: string[];
  versao: number;
}

export interface GrupoModificador {
  id: string;
  nome: string;
  nomeInterno?: string | null;
  minSelecao: number;
  maxSelecao: number;
  permiteRepeticao: boolean;
  ordem: number;
  ativo: boolean;
  versao: number;
  modificadores: OpcaoModificador[];
}

export interface VinculoGrupoResponse {
  grupo: GrupoModificador;
  ordem: number;
  minSelecaoOverride?: number | null;
  maxSelecaoOverride?: number | null;
  minSelecaoEfetiva: number;
  maxSelecaoEfetiva: number;
}

export interface ComboSlotOpcao {
  produtoId: string;
  produtoNome: string;
  acrescimoCentavos: number;
  padrao: boolean;
  ordem: number;
  disponivel: boolean;
}

export interface ComboSlot {
  id: string;
  nome: string;
  minSelecao: number;
  maxSelecao: number;
  obrigatorio: boolean;
  ordem: number;
  versao?: number;
  opcoes: ComboSlotOpcao[];
}

export interface Produto {
  id: string;
  categoriaId: string;
  sku: string;
  nome: string;
  descricao?: string | null;
  imagemUrl?: string | null;
  tipo: TipoProduto;
  precoBaseCentavos: number;
  estacaoPreparo: EstacaoPreparo;
  estacaoPreparoEfetiva: EstacaoPreparo;
  ativo: boolean;
  pausado: boolean;
  disponivel: boolean;
  motivoIndisponibilidade?: string | null;
  ordem: number;
  versao: number;
  insumoIds: string[];
  gruposModificadores: VinculoGrupoResponse[];
  comboSlots: ComboSlot[];
}

export interface ProdutoRequest {
  categoriaId: string;
  sku: string;
  nome: string;
  descricao?: string | null;
  imagemUrl?: string | null;
  tipo: TipoProduto;
  precoBaseCentavos: number;
  estacaoPreparo: EstacaoPreparo;
  ativo: boolean;
  disponivel: boolean;
  ordem: number;
  versao?: number;
}

export interface HistoricoPreco {
  id: string;
  entidadeTipo: 'PRODUTO' | 'MODIFICADOR';
  entidadeId: string;
  tipoPrecoAnterior: TipoPreco;
  valorAnterior: number;
  tipoPrecoNovo: TipoPreco;
  valorNovo: number;
  motivo: string;
  vigenteDesde: string;
  alteradoPor?: string | null;
}

export interface CatalogoEventoStomp {
  versao: number;
  tipo: 'DISPONIBILIDADE_ALTERADA' | 'CATALOGO_ATUALIZADO';
  ocorridoEm: string;
  disponibilidade?: Array<{
    entidade: 'INSUMO' | 'PRODUTO' | 'MODIFICADOR';
    id: string;
    disponivel: boolean;
    motivo?: string | null;
  }>;
  entidadesAfetadas?: string[];
}

// ─────────────────────────── Caixa & Pagamentos ───────────────────────────

export interface UsuarioRef {
  id: string;
  nome: string;
}

export interface TerminalCaixa {
  id: string;
  nome: string;
  possuiGaveta: boolean;
  ativo: boolean;
  sessaoAbertaId?: string | null;
  operadorAtual?: UsuarioRef | null;
}

export interface SessaoCaixaOperador {
  id: string;
  caixaId: string;
  caixaNome: string;
  operador: UsuarioRef;
  status: StatusSessaoCaixa;
  abertaEm: string;
  dataAbertura: string;
  fundoTrocoCentavos: number;
  quantidadePagamentos: number;
  pedidosPosPagoEmAberto: number;
  versao: number;
}

export interface SessaoCaixaGerencial {
  id: string;
  caixaId: string;
  caixaNome: string;
  operador: UsuarioRef;
  status: StatusSessaoCaixa;
  abertaEm: string;
  fechadaEm?: string | null;
  conferidaEm?: string | null;
  conferidaPor?: UsuarioRef | null;
  fundoTrocoCentavos: number;
  esperadoDinheiroCentavos?: number | null;
  declaradoDinheiroCentavos?: number | null;
  diferencaDinheiroCentavos?: number | null;
  possuiSinalizacoes: boolean;
  versao: number;
}

export interface MovimentacaoOperador {
  id: string;
  sessaoCaixaId: string;
  tipo: TipoMovimentacao;
  natureza: NaturezaMovimentacao;
  valorCentavos: number;
  motivo: string;
  usuarioAprovadorId?: string | null;
  aprovadoPor?: UsuarioRef | null;
  createdAt: string;
  createdBy?: UsuarioRef | null;
}

export interface MovimentacaoGerencial extends MovimentacaoOperador {
  pagamentoId?: string | null;
  movimentacaoEstornadaId?: string | null;
  sinalizacao?: string | null;
}

export interface DeclaracaoMetodoRequest {
  metodo: MetodoPagamento;
  valorDeclaradoCentavos: number;
  quantidadeComprovantes?: number | null;
}

export interface FechamentoCegoRequest {
  sessaoCaixaId?: string;
  versao?: number;
  observacao?: string;
  declaracoes: DeclaracaoMetodoRequest[];
}

export interface FechamentoProtocoloResponse {
  sessaoId: string;
  protocolo: string;
  status: StatusSessaoCaixa;
  fechadaEm: string;
  alertas: string[];
}

export interface ConferenciaMetodoItem {
  metodo: MetodoPagamento;
  esperadoCentavos: number;
  declaradoCentavos: number;
  diferencaCentavos: number;
  situacao: SituacaoConferencia;
  quantidadeComprovantesDeclarada?: number | null;
  quantidadePagamentos: number;
}

export interface ComposicaoDinheiro {
  aberturaCentavos: number;
  vendasCentavos: number;
  suprimentosCentavos: number;
  sangriasCentavos: number;
  estornosCentavos: number;
  esperadoCentavos: number;
}

export interface ConferenciaResponse {
  sessaoId: string;
  status: StatusSessaoCaixa;
  operador?: UsuarioRef | null;
  abertaEm: string;
  fechadaEm?: string | null;
  porMetodo: ConferenciaMetodoItem[];
  composicaoDinheiro: ComposicaoDinheiro;
  sinalizacoes: MovimentacaoGerencial[];
  observacaoOperador?: string | null;
  observacaoConferencia?: string | null;
}

// ─────────────────────────── Pedidos & KDS ───────────────────────────

export interface PagamentoInicialRequest {
  sessaoCaixaId?: string;
  metodo: MetodoPagamento;
  valorCentavos: number;
  valorRecebidoCentavos?: number | null;
  nsu?: string | null;
  autorizacao?: string | null;
  txidPix?: string | null;
}

export interface PagamentoRequest extends PagamentoInicialRequest {
  pedidoId: string;
  sessaoCaixaId: string;
}

export interface PagamentoResponse {
  id: string;
  pedidoId: string;
  sessaoCaixaId: string;
  metodo: MetodoPagamento;
  valorCentavos: number;
  valorRecebidoCentavos?: number | null;
  trocoCentavos?: number | null;
  status: StatusPagamento;
  nsu?: string | null;
  autorizacao?: string | null;
  txidPix?: string | null;
  pedidoStatusApos: StatusPedido;
  saldoDevedorAposCentavos: number;
  motivoEstorno?: string | null;
  estornadoEm?: string | null;
  createdAt: string;
}

export interface ModificadorSelecionadoRequest {
  modificadorId: string;
  quantidade: number;
}

export interface SelecaoComboSlotRequest {
  slotId: string;
  produtoId: string;
  observacao?: string;
  modificadores?: ModificadorSelecionadoRequest[];
}

export interface ItemPedidoRequest {
  produtoId: string;
  quantidade: number;
  observacao?: string;
  modificadores?: ModificadorSelecionadoRequest[];
  selecoesCombo?: SelecaoComboSlotRequest[];
}

export interface CriarPedidoRequest {
  origem: OrigemPedido;
  tipoAtendimento: TipoAtendimento;
  modalidadePagamento: ModalidadePagamento;
  sessaoCaixaId?: string;
  clienteId?: string;
  identificadorCliente?: string;
  mesa?: string;
  observacao?: string;
  descontoCentavos?: number;
  catalogoVersao?: number;
  totalEsperadoCentavos?: number;
  pagamentoInicial?: PagamentoInicialRequest;
  itens: ItemPedidoRequest[];
}

export interface ItemPedidoModificadorResponse {
  modificadorId: string;
  grupoNome: string;
  modificadorNome: string;
  tipoPreco: TipoPreco;
  valorPreco: number;
  quantidade: number;
  valorCalculadoCentavos: number;
}

export interface ItemPedidoResponse {
  id: string;
  produtoId: string;
  produtoNome: string;
  quantidade: number;
  precoBaseCentavos: number;
  precoUnitarioFinalCentavos: number;
  totalCentavos: number;
  estacaoPreparo: EstacaoPreparo;
  observacao?: string | null;
  comboSlotNome?: string | null;
  modificadores: ItemPedidoModificadorResponse[];
  filhos: ItemPedidoResponse[];
}

export interface PedidoResponse {
  id: string;
  sessaoCaixaId?: string | null;
  clienteId?: string | null;
  numeroDiario: number;
  dataOperacional: string;
  origem: OrigemPedido;
  tipoAtendimento: TipoAtendimento;
  modalidadePagamento: ModalidadePagamento;
  identificadorCliente?: string | null;
  mesa?: string | null;
  observacao?: string | null;
  status: StatusPedido;
  versao: number;
  itens: ItemPedidoResponse[];
  subtotalCentavos: number;
  descontoCentavos: number;
  totalCentavos: number;
  valorTotalCentavos: number;
  totalPagoCentavos: number;
  saldoDevedorCentavos: number;
  pagamentos: PagamentoResponse[];
  motivoCancelamento?: string | null;
  fiscalStatus?: string | null;
  confirmadoEm?: string | null;
  preparoIniciadoEm?: string | null;
  prontoEm?: string | null;
  entregueEm?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface PedidoCozinhaEventoDto {
  seq: number;
  tipo: string;
  pedidoId: string;
  versao: number;
  status: StatusPedido;
  ocorridoEm: string;
  motivo?: string | null;
}

export interface PedidoProntoEventoDto {
  pedidoId: string;
  numeroDiario: number;
  identificadorCliente?: string | null;
  mesa?: string | null;
}
