# Catálogo de Códigos de Erro & Contratos STOMP (Fase 1)

> [!IMPORTANT]
> Este documento complementa a especificação OpenAPI 3.1 em [openapi.yaml](file:///C:/Users/Rondão/.gemini/antigravity/brain/d390752c-3fb9-44d5-9856-cc6ca1a449c1/contratos/openapi.yaml). Todos os clientes (App Android, Web Master e Web KDS) devem tratar erros exclusivamente pelo campo `code` (nunca por `title` ou `detail`) e seguir os protocolos STOMP descritos aqui.

---

## 1. Envelope Padrão de Erro (RFC 7807 Problem Details)

Toda resposta HTTP com status `>= 400` utiliza `Content-Type: application/problem+json` e respeita o schema `Problem` definido em [openapi.yaml](file:///C:/Users/Rondão/.gemini/antigravity/brain/d390752c-3fb9-44d5-9856-cc6ca1a449c1/contratos/openapi.yaml#L825-L853):

```json
{
  "type": "https://api.cafeteria.local/erros/transicao-concorrente",
  "title": "Transição de status concorrente",
  "status": 409,
  "code": "TRANSICAO_CONCORRENTE",
  "detail": "O pedido #57 mudou para EM_PREPARO (versão 3) antes desta requisição.",
  "instance": "/api/v1/pedidos/019324b8-7e20-7a10-9a11-0242ac120002/cancelar",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "erros": [],
  "estadoAtual": {
    "status": "EM_PREPARO",
    "versao": 3
  },
  "itensIndisponiveis": []
}
```

### Regras de campos adicionais por classe de erro
- `erros[]`: Preenchido em `VALIDACAO_CAMPOS` (`400`) ou quando múltiplos campos falham na validação de negócio (`422`).
- `estadoAtual`: Preenchido obrigatoriamente em `VERSAO_CONFLITANTE` e `TRANSICAO_CONCORRENTE` (`409`), permitindo que o frontend ou Android atualize sua store local sem precisar de um `GET` extra.
- `itensIndisponiveis[]`: Preenchido em `ITEM_INDISPONIVEL` (`422`), listando os UUIDs dos produtos, modificadores ou opções de combo que esgotaram entre a montagem do carrinho e o envio do pedido.

---

## 2. Catálogo Completo de Códigos de Negócio (`CodigoErro`)

### 2.1 Infraestrutura, Validação & Autenticação

| `code` | HTTP | Quando ocorre | Ação esperada no Cliente (Web / Android) |
|---|:-:|---|---|
| `VALIDACAO_CAMPOS` | 400 | Bean Validation falhou (`@NotNull`, `@Size`, `@Min`, regex). | Destacar os campos listados em `erros[]` no formulário. |
| `JSON_INVALIDO` | 400 | Payload malformado, enum desconhecido ou tipo incompatível. | Erro de contrato/desenvolvimento; logar `traceId`. |
| `NAO_AUTENTICADO` | 401 | Header `Authorization` ausente ou token malformado. | Redirecionar para login. |
| `TOKEN_EXPIRADO` | 401 | Access token expirou. | Acionar interceptor de `POST /auth/refresh` silenciosamente; se falhar, tela de login. |
| `CREDENCIAIS_INVALIDAS` | 401 | Login/senha incorretos ou usuário inativo. | Exibir mensagem no formulário de login. |
| `ACESSO_NEGADO` | 403 | Role/authority insuficiente para a rota ou recurso de outra loja. | Exibir alerta de permissão negada. |
| `RECURSO_NAO_ENCONTRADO` | 404 | UUID não existe na loja do token (`lojaId`). | Exibir estado vazio / voltar para listagem. |
| `IDEMPOTENCY_KEY_AUSENTE` | 428 | Falta header `Idempotency-Key` em rota que o exige. | Gerar UUIDv7 e reenviar (bug de cliente). |
| `VERSAO_AUSENTE` | 428 | Falta campo `versao` em atualização de recurso versionado. | Recarregar recurso e reenviar com `versao`. |
| `IDEMPOTENCY_KEY_REUTILIZADA` | 409 | Mesma `Idempotency-Key` enviada com payload diferente do original. | Gerar nova `Idempotency-Key` se for uma nova intenção do usuário. |
| `VERSAO_CONFLITANTE` | 409 | Optimistic locking (`versao` enviada ≠ `versao` no banco). | Aplicar `estadoAtual` ou recarregar o registro e avisar que houve edição simultânea. |
| `ERRO_INTERNO` | 500 | Falha inesperada no servidor. | Exibir `traceId` para suporte e permitir tentar novamente. |

### 2.2 Módulo de Catálogo (Categorias, Produtos, Modificadores, Combos, Insumos)

| `code` | HTTP | Quando ocorre | Ação esperada no Cliente (Master / Android) |
|---|:-:|---|---|
| `SKU_DUPLICADO` | 409 | Já existe produto com o mesmo `sku` na loja. | Destacar campo `sku`. |
| `SLUG_DUPLICADO` | 409 | Já existe categoria irmã com o mesmo `slug`. | Sugerir outro nome/slug. |
| `NOME_DUPLICADO` | 409 | Nome duplicado em insumo ou modificador dentro do mesmo grupo. | Destacar campo `nome`. |
| `CATEGORIA_PROFUNDIDADE_EXCEDIDA` | 422 | Tentativa de criar subcategoria de uma subcategoria (> 2 níveis). | Bloquear seleção de subcategoria como pai na UI. |
| `CATEGORIA_COM_PRODUTOS_ATIVOS` | 409 | Tentativa de arquivar categoria que ainda possui produtos ativos/não arquivados. | Solicitar remanejamento ou arquivamento prévio dos produtos. |
| `CATEGORIA_ARQUIVADA` | 422 | Tentativa de vincular produto a uma categoria arquivada. | Atualizar lista de categorias disponíveis. |
| `GRUPO_EM_USO` | 409 | Tentativa de arquivar grupo de modificadores vinculado a produto ativo. | Listar produtos vinculados (`produtosVinculados`) para desvincular antes. |
| `LIMITES_SELECAO_INVALIDOS` | 422 | `minSelecao < 0`, `maxSelecao < minSelecao`, `maxSelecao < 1`, ou mais de um `padrao` quando `maxSelecao = 1`. | Destacar campos de mínimo/máximo/padrão. |
| `PRECO_INVALIDO` | 422 | Preço base negativo ou desconto percentual que resulte em preço unitário `< 0`. | Destacar campo de valor/percentual. |
| `PRODUTO_NAO_E_COMBO` | 422 | Chamada a `PUT /produtos/{id}/combo-slots` em produto `SIMPLES`. | Ocultar aba de slots para produtos simples. |
| `COMBO_ANINHADO` | 422 | Tentativa de incluir um produto `COMBO` como opção dentro do slot de outro combo. | Filtrar apenas produtos `SIMPLES` no seletor de opções de slot. |

### 2.3 Módulo de Pedidos & KDS

| `code` | HTTP | Quando ocorre | Ação esperada no Cliente (Android / Balcão / KDS) |
|---|:-:|---|---|
| `ITEM_INDISPONIVEL` | 422 | Produto, modificador ou opção de combo pausado/esgotado no instante do `POST /pedidos`. | Ler `itensIndisponiveis[]`, sinalizar os itens no carrinho e disparar refresh do catálogo. |
| `MODIFICADOR_OBRIGATORIO` | 422 | Grupo vinculado ao produto possui `minSelecaoEfetiva >= 1` e não recebeu seleção suficiente. | Destacar grupo obrigatório no modal do item. |
| `MODIFICADOR_LIMITE` | 422 | Quantidade selecionada no grupo excede `maxSelecaoEfetiva` ou `maxQuantidade` do modificador. | Ajustar seleção aos limites. |
| `MODIFICADOR_NAO_PERTENCE_PRODUTO` | 422 | Modificador enviado não pertence a nenhum grupo vinculado àquele produto. | Forçar atualização do catálogo local (`GET /catalogo`). |
| `MODIFICADOR_REPETICAO_NAO_PERMITIDA` | 422 | `quantidade > 1` enviada em grupo com `permiteRepeticao = false`. | Ajustar quantidade para 1. |
| `COMBO_SLOT_INVALIDO` | 422 | Slot obrigatório do combo não preenchido ou produto enviado não pertence às opções do slot. | Destacar slot pendente no configurador do combo. |
| `PRECO_DESATUALIZADO` | 409 | `catalogoVersao` ou `totalEsperadoCentavos` enviado pelo cliente diverge do cálculo atual do servidor. | Atualizar catálogo (`GET /catalogo`), exibir o novo total ao atendente e pedir confirmação para reenviar. |
| `TRANSICAO_CONCORRENTE` | 409 | Outro ator alterou o status do pedido antes (ex.: cozinha iniciou preparo enquanto atendente tentava cancelar). | Aplicar `estadoAtual` na UI. Se era tentativa de cancelamento pelo atendente e foi para `EM_PREPARO`, abrir modal solicitando PIN/ação do gerente. |
| `TRANSICAO_INVALIDA` | 422 | Transição não existe na máquina de estados (ex.: `ENTREGUE` → `EM_PREPARO`). | Atualizar card do pedido. |
| `PEDIDO_NAO_QUITADO` | 422 | Tentativa de confirmar pedido `PRE_PAGO` com `saldoDevedorCentavos > 0`. | Redirecionar para tela de pagamento. |
| `CANCELAMENTO_REQUER_GERENTE` | 403 | `ATENDENTE` tentou cancelar pedido em `EM_PREPARO`, `PRONTO` ou `ENTREGUE`. | Solicitar autenticação/PIN de `GERENTE`. |
| `EVENTOS_EXPIRADOS` | 410 | `desdeSeq` enviado em `GET /cozinha/eventos` é antigo demais (fora da janela de retenção). | Descartar estado local do KDS e chamar `GET /cozinha/pedidos/ativos` (snapshot completo). |

### 2.4 Módulo Financeiro (Pagamentos & Caixa)

| `code` | HTTP | Quando ocorre | Ação esperada no Cliente (Master / Balcão) |
|---|:-:|---|---|
| `SESSAO_CAIXA_FECHADA` | 422 | Tentativa de registrar pagamento, sangria ou suprimento em sessão que já não está `ABERTA`. | Solicitar abertura de nova sessão de caixa. |
| `SESSAO_CAIXA_INEXISTENTE` | 422 | Cancelamento/estorno com devolução em dinheiro sem sessão aberta informada. | Informar sessão aberta atual para registrar a saída em espécie. |
| `CAIXA_JA_ABERTO` | 409 | Tentativa de abrir sessão em terminal que já possui sessão `ABERTA`. | Carregar a sessão aberta existente (`GET /caixa/sessoes/atual`). |
| `OPERADOR_COM_SESSAO` | 409 | Operador já possui outra sessão aberta em outro terminal. | Informar qual terminal está aberto para o operador. |
| `VALOR_EXCEDE_SALDO_DEVEDOR` | 422 | `valorCentavos` do pagamento é maior que `saldoDevedorCentavos` do pedido. | Ajustar `valorCentavos` ao saldo devedor (para dinheiro, o excesso deve ir em `valorRecebidoCentavos` para cálculo de troco). |
| `RECEBIDO_INSUFICIENTE` | 422 | Pagamento em `DINHEIRO` com `valorRecebidoCentavos < valorCentavos`. | Destacar campo de valor recebido. |
| `TROCO_APENAS_DINHEIRO` | 422 | `valorRecebidoCentavos > valorCentavos` enviado para Pix, Cartão ou Voucher. | Bloquear troco em métodos digitais na UI. |
| `PAGAMENTO_JA_ESTORNADO` | 409 | Tentativa de estornar pagamento que já está `ESTORNADO`. | Atualizar dados do pedido. |
| `MOVIMENTACAO_JA_ESTORNADA` | 409 | Movimentação de caixa já possui lançamento reverso vinculado. | Atualizar extrato da sessão. |
| `MOVIMENTACAO_NAO_ESTORNAVEL` | 422 | Tentativa de estornar diretamente `ABERTURA` ou `VENDA_DINHEIRO` avulsa (vendas são estornadas via pagamento/pedido). | Orientar estorno pelo pedido/pagamento correspondente. |
| `APROVACAO_NECESSARIA` | 403 | Sangria acima do limite da loja enviada por `ATENDENTE` sem `pinAprovador`. | Abrir modal solicitando PIN de `GERENTE` e reenviar. |
| `PIN_APROVADOR_INVALIDO` | 403 | `pinAprovador` não corresponde a nenhum `GERENTE`/`ADMIN` ativo da loja. | Limpar campo de PIN e exibir erro. |
| `DECLARACAO_DINHEIRO_OBRIGATORIA` | 422 | Fechamento cego enviado sem declaração para o método `DINHEIRO`. | Exigir preenchimento do campo Dinheiro (mesmo que `0`). |
| `METODO_DECLARADO_DUPLICADO` | 422 | Mais de um item para o mesmo `MetodoPagamento` em `declaracoes[]`. | Consolidar por método antes de enviar. |
| `SESSAO_NAO_FECHADA` | 409 | Chamada a `/conferencia` ou `/conferir` em sessão ainda `ABERTA`. | Aguardar fechamento pelo operador. |
| `SESSAO_DE_OUTRO_OPERADOR` | 403 | `ATENDENTE` tentou fechar sessão aberta por outro operador (apenas o próprio operador ou `GERENTE+` pode fechar). | Solicitar presença do operador titular ou gerente. |
| `IMPRESSORA_INDISPONIVEL` | 422 | Impressora térmica/gaveta não respondeu na porta 9100 (quando a ação é exclusivamente física, ex.: `abrir-gaveta`). | Exibir alerta de falha na impressora; em vendas/sangrias o lançamento financeiro **não** falha (o job fica em retry na fila de impressão). |

---

## 3. Especificação do Protocolo STOMP / WebSocket

### 3.1 Handshake, Autenticação e Heartbeat
- **Endpoint WebSocket:** `wss://cafeteria.local/ws` (ou `ws://localhost:8080/ws` em dev) — STOMP 1.2 sobre WebSocket nativo (sem SockJS).
- **Autenticação:** no frame `CONNECT`, enviar header `Authorization: Bearer <accessToken>`. Se o token expirar durante a conexão, o cliente renova via REST e reconecta no WebSocket.
- **Heartbeat:** `heart-beat: 10000,10000` (10s cliente → servidor, 10s servidor → cliente). Se não houver tráfego por > 20s, o cliente considera a conexão morta e inicia reconexão com backoff exponencial (`500ms`, `1s`, `2s`, `5s` máx + jitter).

### 3.2 Tópicos (Escopo por Loja)

O servidor valida se o `lojaId` da assinatura coincide com a claim `lojaId` do JWT (`403` frame `ERROR` caso contrário).

| Destino STOMP | Quem assina | Schema do Payload (em `openapi.yaml`) | Garantias e Comportamento |
|---|---|---|---|
| `/topic/lojas/{lojaId}/cozinha/pedidos` | KDS (visão geral) | `PedidoCozinhaEvento` | Contém `seq` monotônico e `versao` do pedido. Em `PEDIDO_CONFIRMADO` e `PEDIDO_REFEITO` inclui o objeto `pedido` completo; nos demais eventos pode conter apenas metadados e novo status. |
| `/topic/lojas/{lojaId}/cozinha/estacoes/{estacao}` | KDS filtrado (`BAR` ou `COZINHA`) | `PedidoCozinhaEvento` | Mesmo contrato, porém `pedido.itens` filtrado para a estação assinada. |
| `/topic/lojas/{lojaId}/pedidos/prontos` | App Android, Painel de Senhas | `PedidoProntoEvento` | Disparado na transição `EM_PREPARO → PRONTO`. |
| `/topic/lojas/{lojaId}/catalogo` | App Android, Web Master | `CatalogoEvento` | Disparado em alterações cadastrais/preço. Cliente compara `versao` com cache local e chama `GET /catalogo` com `If-None-Match`. |
| `/topic/lojas/{lojaId}/disponibilidade` | App Android, Web Master, KDS | `CatalogoEvento` (`tipo = DISPONIBILIDADE_ALTERADA`) | Contém o array `disponibilidade[]` com o resultado do cálculo em cascata — o cliente aplica diretamente na store local sem precisar baixar o catálogo inteiro. |
| `/user/queue/notificacoes` | Web Master (`GERENTE+`) | `NotificacaoGerencial` | Alertas em tempo real: sangria com `SALDO_ESPERADO_NEGATIVO`, falha na fila de impressão (`IMPRESSORA_INDISPONIVEL`), fechamento com quebra acima da tolerância. |

### 3.3 Algoritmo Determinístico de Reconexão do KDS

1. **Boot inicial do KDS:**
   - Assina o tópico STOMP e **acumula** eventos recebidos num buffer temporário em memória.
   - Chama `GET /api/v1/cozinha/pedidos/ativos`.
   - Renderiza `pedidos[]`, salva `ultimoSeq = seqAtual` e processa o buffer aplicando apenas eventos com `evento.seq > seqAtual` e `evento.versao > pedidoLocal.versao`.
2. **Em operação normal:**
   - Ao receber `evento`:
     - Se `evento.seq <= ultimoSeq`: descarta (duplicado).
     - Se `evento.seq == ultimoSeq + 1`: aplica na store (se `evento.versao > pedidoLocal.versao`) e atualiza `ultimoSeq = evento.seq`.
     - Se `evento.seq > ultimoSeq + 1` (gap detectado): entra imediatamente no fluxo de replay (passo 3).
3. **Na reconexão ou detecção de gap:**
   - Pausa aplicação direta e volta a acumular mensagens STOMP no buffer.
   - Chama `GET /api/v1/cozinha/eventos?desdeSeq={ultimoSeq}`.
   - **Se retornar `200 OK`:** aplica a lista ordenada de eventos, atualiza `ultimoSeq`, drena o buffer e volta ao modo normal.
   - **Se retornar `410 Gone` (`EVENTOS_EXPIRADOS`):** executa o passo 1 completo (snapshot).
