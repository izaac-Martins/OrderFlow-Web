// ═══════════════════════════════════════════════════════════════════════════
// Parser Padronizado RFC 7807 (ProblemDetail) e Dicionário de Mensagens Amigáveis
// ═══════════════════════════════════════════════════════════════════════════

import axios from 'axios';
import type { CodigoErro, ProblemDetail } from '../types/api';

const FRIENDLY_ERROR_MESSAGES: Record<CodigoErro, string> = {
  NAO_AUTENTICADO: 'Sua sessão expirou ou você não está autenticado. Faça login novamente.',
  TOKEN_EXPIRADO: 'Seu token de acesso expirou. Renovando sessão...',
  CREDENCIAIS_INVALIDAS: 'E-mail, senha ou loja inválidos. Verifique os dados informados.',
  CONTA_BLOQUEADA: 'Este usuário está temporariamente desativado. Contate o administrador.',
  REFRESH_TOKEN_INVALIDO: 'Sua sessão expirou. Por favor, autentique-se novamente.',
  ACESSO_NEGADO: 'Seu perfil de acesso não possui permissão para executar esta operação.',
  APROVACAO_NECESSARIA:
    'Esta operação ultrapassa o limite operacional e exige autorização por PIN de um GERENTE.',
  PIN_APROVADOR_INVALIDO:
    'O PIN de alçada gerencial informado é inválido ou não pertence a um gerente ativo desta loja.',
  CANCELAMENTO_REQUER_GERENTE:
    'Este pedido já entrou em preparo na cozinha. O cancelamento exige o PIN de alçada de um GERENTE.',
  SESSAO_DE_OUTRO_OPERADOR:
    'Esta sessão de caixa pertence a outro operador. Apenas o titular ou um GERENTE pode fechá-la.',
  RECURSO_NAO_ENCONTRADO: 'O registro solicitado não foi encontrado nesta loja.',
  VERSAO_CONFLITANTE:
    'Este registro foi alterado por outro terminal. Os dados foram atualizados na tela; revise e tente novamente.',
  TRANSICAO_CONCORRENTE:
    'O status deste pedido mudou em outro terminal (ex.: KDS da cozinha) antes da sua ação.',
  IDEMPOTENCIA_CONFLITO:
    'Uma operação idêntica já está sendo processada ou foi enviada com dados divergentes.',
  SKU_DUPLICADO: 'Já existe um produto cadastrado com este SKU nesta loja.',
  SLUG_DUPLICADO: 'Já existe uma categoria com este slug no mesmo nível hierárquico.',
  NOME_DUPLICADO: 'Já existe um registro com este nome nesta loja.',
  EMAIL_DUPLICADO: 'Este endereço de e-mail já está em uso nesta loja.',
  CATEGORIA_COM_PRODUTOS_ATIVOS:
    'Não é possível arquivar uma categoria que ainda possui produtos ativos vinculados.',
  CAIXA_JA_ABERTO:
    'Este terminal de caixa já possui uma sessão ABERTA. Feche a sessão atual antes de abrir outra.',
  OPERADOR_COM_SESSAO:
    'Você já possui uma sessão de caixa aberta em outro terminal desta loja.',
  PAGAMENTO_JA_ESTORNADO: 'Este pagamento já foi estornado anteriormente.',
  MOVIMENTACAO_JA_ESTORNADA: 'Esta movimentação de caixa já possui um lançamento reverso de estorno.',
  EVENTOS_EXPIRADOS: 'A fila de eventos expirou. Recarregando o snapshot completo...',
  VALIDACAO_CAMPOS: 'Alguns campos informados possuem valores inválidos. Verifique o formulário.',
  VERSAO_AUSENTE: 'A versão do registro é obrigatória para controle de concorrência.',
  IDEMPOTENCY_KEY_AUSENTE: 'Chave de idempotência ausente na requisição.',
  CATEGORIA_PROFUNDIDADE_EXCEDIDA:
    'A hierarquia de categorias suporta no máximo 2 níveis (Categoria Principal → Subcategoria).',
  CICLO_CATEGORIA: 'Uma categoria não pode ser subcategoria de si mesma ou de suas descendentes.',
  PRECO_INVALIDO: 'O preço resultante em centavos não pode ser negativo.',
  LIMITE_DESCONTO_PERCENTUAL: 'O desconto percentual excede o limite permitido.',
  LIMITES_SELECAO_INVALIDOS:
    'Os limites mínimo e máximo de seleção são inválidos (exige mín >= 0, máx >= 1 e máx >= mín).',
  COMBO_ANINHADO:
    'Combos não podem conter outros combos dentro dos seus slots (apenas produtos SIMPLES).',
  PRODUTO_NAO_E_COMBO: 'Apenas produtos do tipo COMBO aceitam configuração de slots.',
  ITEM_INDISPONIVEL: 'Este item está esgotado no momento.',
  ITENS_INDISPONIVEIS:
    'Um ou mais produtos ou adicionais selecionados acabaram de esgotar na cozinha/bar.',
  MODIFICADOR_OBRIGATORIO: 'Selecione todas as opções obrigatórias antes de adicionar o item.',
  MODIFICADOR_LIMITE: 'A quantidade selecionada excede o limite máximo permitido pelo grupo.',
  MODIFICADOR_NAO_PERTENCE_PRODUTO:
    'O adicional selecionado não pertence a nenhum grupo vinculado a este produto.',
  MODIFICADOR_REPETICAO_NAO_PERMITIDA:
    'Este grupo de modificadores não permite repetir a mesma opção.',
  COMBO_SLOT_INVALIDO: 'Preencha corretamente todas as escolhas obrigatórias do combo.',
  PRECO_DESATUALIZADO:
    'O preço de um item do carrinho foi atualizado no catálogo. Confira o novo total.',
  TRANSICAO_INVALIDA: 'Esta mudança de status não é permitida para o estado atual do pedido.',
  PEDIDO_NAO_QUITADO:
    'Pedidos pré-pagos precisam estar integralmente quitados antes do envio para preparo.',
  VALOR_EXCEDE_SALDO_DEVEDOR: 'O valor do pagamento excede o saldo devedor restante do pedido.',
  RECEBIDO_INSUFICIENTE:
    'O valor recebido em espécie é menor que o valor cobrado no pagamento.',
  TROCO_APENAS_DINHEIRO: 'Cálculo de troco na gaveta é exclusivo para pagamentos em DINHEIRO.',
  SESSAO_CAIXA_FECHADA: 'A sessão de caixa informada já foi encerrada.',
  SESSAO_CAIXA_INEXISTENTE:
    'Nenhuma sessão de caixa aberta encontrada. Abra o caixa antes de registrar operações financeiras.',
  DECLARACAO_DINHEIRO_OBRIGATORIA:
    'A declaração do valor contado em espécie (DINHEIRO) é obrigatória no fechamento cego.',
  METODO_DECLARADO_DUPLICADO: 'Cada método de pagamento deve ser declarado apenas uma vez.',
  SESSAO_NAO_FECHADA:
    'A conferência gerencial só fica disponível após o operador concluir o fechamento cego.',
  MOVIMENTACAO_NAO_ESTORNAVEL:
    'Apenas movimentações manuais de SANGRIA ou SUPRIMENTO permitem lançamento reverso direto.',
  IMPRESSORA_INDISPONIVEL: 'Não foi possível comunicar com a impressora térmica do terminal.',
  ERRO_INTERNO: 'Ocorreu um erro interno inesperado. Tente novamente em instantes.',
};

export class OrderFlowApiError extends Error {
  public readonly problem: ProblemDetail;

  constructor(problem: ProblemDetail) {
    const friendly =
      FRIENDLY_ERROR_MESSAGES[problem.code] ||
      problem.detail ||
      problem.title ||
      'Erro na operação.';
    super(friendly);
    this.name = 'OrderFlowApiError';
    this.problem = problem;
  }

  get code(): CodigoErro {
    return this.problem.code;
  }

  get status(): number {
    return this.problem.status;
  }

  requiresManagerPin(): boolean {
    return (
      this.problem.code === 'APROVACAO_NECESSARIA' ||
      this.problem.code === 'CANCELAMENTO_REQUER_GERENTE'
    );
  }
}

/**
 * Converte qualquer erro Axios/Fetch ou objeto ProblemDetail em um OrderFlowApiError padronizado.
 */
export function parseApiError(err: unknown): OrderFlowApiError {
  if (err instanceof OrderFlowApiError) {
    return err;
  }

  if (axios.isAxiosError(err) && err.response?.data) {
    const data = err.response.data as Partial<ProblemDetail>;
    if (data && typeof data === 'object' && data.code) {
      return new OrderFlowApiError({
        type: data.type ?? 'about:blank',
        title: data.title ?? 'Erro de negócio',
        status: data.status ?? err.response.status,
        code: data.code,
        detail: data.detail ?? FRIENDLY_ERROR_MESSAGES[data.code] ?? 'Erro na requisição',
        instance: data.instance,
        traceId: data.traceId,
        erros: data.erros ?? [],
        estadoAtual: data.estadoAtual,
        itensIndisponiveis: data.itensIndisponiveis ?? [],
      });
    }
  }

  if (err && typeof err === 'object' && 'code' in err && 'status' in err) {
    const p = err as ProblemDetail;
    return new OrderFlowApiError(p);
  }

  return new OrderFlowApiError({
    type: 'https://api.cafeteria.local/erros/erro-interno',
    title: 'Erro de comunicação',
    status: 500,
    code: 'ERRO_INTERNO',
    detail: err instanceof Error ? err.message : 'Falha inesperada na comunicação com o servidor.',
  });
}

export function getFriendlyErrorMessage(code: CodigoErro, fallback?: string): string {
  return FRIENDLY_ERROR_MESSAGES[code] || fallback || 'Não foi possível concluir a operação.';
}
