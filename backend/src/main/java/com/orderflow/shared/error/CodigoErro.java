package com.orderflow.shared.error;

import org.springframework.http.HttpStatus;

/**
 * Catálogo oficial e exaustivo de códigos de erro de negócio do Ecossistema OrderFlow.
 * Sincronizado 1:1 com {@code contratos/openapi.yaml} e {@code contratos/catalogo_erros.md}.
 */
public enum CodigoErro {

    // 2.1 Infraestrutura, Validação & Autenticação
    VALIDACAO_CAMPOS(HttpStatus.BAD_REQUEST, "validacao-campos", "Falha na validação dos campos enviados"),
    JSON_INVALIDO(HttpStatus.BAD_REQUEST, "json-invalido", "Payload JSON malformado ou tipo incompatível"),
    NAO_AUTENTICADO(HttpStatus.UNAUTHORIZED, "nao-autenticado", "Autenticação necessária"),
    TOKEN_EXPIRADO(HttpStatus.UNAUTHORIZED, "token-expirado", "Token de acesso expirado"),
    CREDENCIAIS_INVALIDAS(HttpStatus.UNAUTHORIZED, "credenciais-invalidas", "Credenciais inválidas ou usuário inativo"),
    ACESSO_NEGADO(HttpStatus.FORBIDDEN, "acesso-negado", "Permissão insuficiente para executar esta operação"),
    RECURSO_NAO_ENCONTRADO(HttpStatus.NOT_FOUND, "recurso-nao-encontrado", "Recurso não encontrado"),
    VERSAO_CONFLITANTE(HttpStatus.CONFLICT, "versao-conflitante", "Conflito de versão por atualização concorrente"),
    TRANSICAO_CONCORRENTE(HttpStatus.CONFLICT, "transicao-concorrente", "Transição de status concorrente não permitida"),
    TRANSICAO_INVALIDA(HttpStatus.UNPROCESSABLE_ENTITY, "transicao-invalida", "Transição de estado inválida"),
    IDEMPOTENCY_KEY_REUTILIZADA(HttpStatus.CONFLICT, "idempotency-key-reutilizada", "Idempotency-Key já utilizada com payload diferente"),
    IDEMPOTENCY_KEY_AUSENTE(HttpStatus.PRECONDITION_REQUIRED, "idempotency-key-ausente", "Header Idempotency-Key é obrigatório"),
    VERSAO_AUSENTE(HttpStatus.PRECONDITION_REQUIRED, "versao-ausente", "Campo versao é obrigatório para controle de concorrência"),
    ERRO_INTERNO(HttpStatus.INTERNAL_SERVER_ERROR, "erro-interno", "Erro interno inesperado no servidor"),

    // 2.2 Módulo de Catálogo
    SKU_DUPLICADO(HttpStatus.CONFLICT, "sku-duplicado", "Já existe um produto com este SKU na loja"),
    SLUG_DUPLICADO(HttpStatus.CONFLICT, "slug-duplicado", "Já existe uma categoria com este slug na loja"),
    NOME_DUPLICADO(HttpStatus.CONFLICT, "nome-duplicado", "Já existe um registro com este nome no mesmo escopo"),
    CATEGORIA_PROFUNDIDADE_EXCEDIDA(HttpStatus.UNPROCESSABLE_ENTITY, "categoria-profundidade-excedida", "Hierarquia de categorias suporta no máximo 2 níveis"),
    CATEGORIA_COM_PRODUTOS_ATIVOS(HttpStatus.CONFLICT, "categoria-com-produtos-ativos", "Categoria possui produtos ativos e não pode ser arquivada"),
    CATEGORIA_ARQUIVADA(HttpStatus.UNPROCESSABLE_ENTITY, "categoria-arquivada", "Não é permitido vincular produtos a uma categoria arquivada"),
    GRUPO_EM_USO(HttpStatus.CONFLICT, "grupo-em-uso", "Grupo de modificadores vinculado a produtos ativos"),
    LIMITES_SELECAO_INVALIDOS(HttpStatus.UNPROCESSABLE_ENTITY, "limites-selecao-invalidos", "Limites de seleção (mínimo/máximo/padrão) inválidos"),
    PRECO_INVALIDO(HttpStatus.UNPROCESSABLE_ENTITY, "preco-invalido", "Valor monetário ou percentual resulta em preço inválido"),
    PRODUTO_NAO_E_COMBO(HttpStatus.UNPROCESSABLE_ENTITY, "produto-nao-e-combo", "Operação de slots permitida apenas para produtos do tipo COMBO"),
    COMBO_ANINHADO(HttpStatus.UNPROCESSABLE_ENTITY, "combo-aninhado", "Um combo não pode conter outro combo como opção de slot"),

    // 2.3 Módulo de Pedidos & KDS
    ITEM_INDISPONIVEL(HttpStatus.UNPROCESSABLE_ENTITY, "item-indisponivel", "Um ou mais itens selecionados estão indisponíveis ou esgotados"),
    MODIFICADOR_OBRIGATORIO(HttpStatus.UNPROCESSABLE_ENTITY, "modificador-obrigatorio", "Seleção obrigatória de modificadores não atendida"),
    MODIFICADOR_LIMITE(HttpStatus.UNPROCESSABLE_ENTITY, "modificador-limite", "Quantidade de modificadores excede o limite permitido"),
    MODIFICADOR_NAO_PERTENCE_PRODUTO(HttpStatus.UNPROCESSABLE_ENTITY, "modificador-nao-pertence-produto", "Modificador selecionado não pertence aos grupos do produto"),
    MODIFICADOR_REPETICAO_NAO_PERMITIDA(HttpStatus.UNPROCESSABLE_ENTITY, "modificador-repeticao-nao-permitida", "Grupo de modificadores não permite repetição do mesmo item"),
    COMBO_SLOT_INVALIDO(HttpStatus.UNPROCESSABLE_ENTITY, "combo-slot-invalido", "Seleção de opções para o slot do combo é inválida ou incompleta"),
    PRECO_DESATUALIZADO(HttpStatus.CONFLICT, "preco-desatualizado", "O catálogo mudou e o preço calculado diverge do esperado pelo cliente"),
    PEDIDO_NAO_QUITADO(HttpStatus.UNPROCESSABLE_ENTITY, "pedido-nao-quitado", "Pedido pré-pago exige quitação integral antes da confirmação"),
    CANCELAMENTO_REQUER_GERENTE(HttpStatus.FORBIDDEN, "cancelamento-requer-gerente", "Cancelamento de pedido em preparo ou posterior exige permissão de GERENTE"),
    EVENTOS_EXPIRADOS(HttpStatus.GONE, "eventos-expirados", "Sequência de eventos solicitada expirou; consulte o snapshot completo"),

    // 2.4 Módulo Financeiro (Pagamentos & Caixa)
    SESSAO_CAIXA_FECHADA(HttpStatus.UNPROCESSABLE_ENTITY, "sessao-caixa-fechada", "A sessão de caixa informada não está aberta"),
    SESSAO_CAIXA_INEXISTENTE(HttpStatus.UNPROCESSABLE_ENTITY, "sessao-caixa-inexistente", "Nenhuma sessão de caixa aberta para registrar a operação"),
    CAIXA_JA_ABERTO(HttpStatus.CONFLICT, "caixa-ja-aberto", "Este terminal de caixa já possui uma sessão aberta"),
    OPERADOR_COM_SESSAO(HttpStatus.CONFLICT, "operador-com-sessao", "Operador já possui uma sessão de caixa aberta em outro terminal"),
    VALOR_EXCEDE_SALDO_DEVEDOR(HttpStatus.UNPROCESSABLE_ENTITY, "valor-excede-saldo-devedor", "Valor do pagamento excede o saldo devedor do pedido"),
    RECEBIDO_INSUFICIENTE(HttpStatus.UNPROCESSABLE_ENTITY, "recebido-insuficiente", "Valor recebido em espécie é inferior ao valor do pagamento"),
    TROCO_APENAS_DINHEIRO(HttpStatus.UNPROCESSABLE_ENTITY, "troco-apenas-dinheiro", "Troco é permitido exclusivamente para pagamentos em DINHEIRO"),
    PAGAMENTO_JA_ESTORNADO(HttpStatus.CONFLICT, "pagamento-ja-estornado", "Este pagamento já foi estornado"),
    MOVIMENTACAO_JA_ESTORNADA(HttpStatus.CONFLICT, "movimentacao-ja-estornada", "Esta movimentação de caixa já possui lançamento reverso"),
    MOVIMENTACAO_NAO_ESTORNAVEL(HttpStatus.UNPROCESSABLE_ENTITY, "movimentacao-nao-estornavel", "Este tipo de movimentação não pode ser estornado diretamente"),
    APROVACAO_NECESSARIA(HttpStatus.FORBIDDEN, "aprovacao-necessaria", "Sangria acima do limite exige PIN de aprovação de um GERENTE"),
    PIN_APROVADOR_INVALIDO(HttpStatus.FORBIDDEN, "pin-aprovador-invalido", "PIN de aprovação gerencial inválido"),
    DECLARACAO_DINHEIRO_OBRIGATORIA(HttpStatus.UNPROCESSABLE_ENTITY, "declaracao-dinheiro-obrigatoria", "Declaração de contagem para DINHEIRO é obrigatória no fechamento cego"),
    METODO_DECLARADO_DUPLICADO(HttpStatus.UNPROCESSABLE_ENTITY, "metodo-declarado-duplicado", "Método de pagamento informado mais de uma vez nas declarações de fechamento"),
    SESSAO_NAO_FECHADA(HttpStatus.CONFLICT, "sessao-nao-fechada", "Conferência exige que a sessão de caixa esteja com status FECHADA"),
    SESSAO_DE_OUTRO_OPERADOR(HttpStatus.FORBIDDEN, "sessao-de-outro-operador", "Apenas o operador titular da sessão ou um GERENTE pode realizar o fechamento"),
    IMPRESSORA_INDISPONIVEL(HttpStatus.UNPROCESSABLE_ENTITY, "impressora-indisponivel", "Impressora térmica ou gaveta não respondeu na rede local");

    private static final String BASE_TYPE_URI = "https://api.cafeteria.local/erros/";

    private final HttpStatus httpStatus;
    private final String slug;
    private final String defaultTitle;

    CodigoErro(HttpStatus httpStatus, String slug, String defaultTitle) {
        this.httpStatus = httpStatus;
        this.slug = slug;
        this.defaultTitle = defaultTitle;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getSlug() {
        return slug;
    }

    public String getDefaultTitle() {
        return defaultTitle;
    }

    public String getTypeUri() {
        return BASE_TYPE_URI + slug;
    }
}
