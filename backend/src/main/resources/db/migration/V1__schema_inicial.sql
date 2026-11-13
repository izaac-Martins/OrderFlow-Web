-- =================================================================================
-- ECOSSISTEMA ORDERFLOW — V1: SCHEMA INICIAL (MySQL 8.0+)
-- Regras de Ouro:
--   1. PKs e FKs em BINARY(16) (UUIDv7 ordenável por tempo).
--   2. Multi-tenant nativo: loja_id BINARY(16) NOT NULL em todas as tabelas.
--   3. Valores monetários estritamente em BIGINT (centavos inteiros).
--   4. Optimistic Locking: versao INT NOT NULL DEFAULT 1 nas tabelas mutáveis.
--   5. Imutabilidade de Caixa/Eventos: Triggers BEFORE UPDATE e BEFORE DELETE.
-- =================================================================================

-- ---------------------------------------------------------------------------------
-- 1. LOJAS E SEGURANÇA (TENANT & RBAC)
-- ---------------------------------------------------------------------------------
CREATE TABLE lojas (
    id                  BINARY(16)   NOT NULL,
    codigo              VARCHAR(30)  NOT NULL,
    nome                VARCHAR(120) NOT NULL,
    cnpj                VARCHAR(18)  NULL,
    fuso_horario        VARCHAR(50)  NOT NULL DEFAULT 'America/Sao_Paulo',
    limite_sangria_sem_aprovacao_centavos BIGINT NOT NULL DEFAULT 50000,
    confirmar_pre_pago_automatico TINYINT(1) NOT NULL DEFAULT 1,
    ativo               TINYINT(1)   NOT NULL DEFAULT 1,
    versao              INT          NOT NULL DEFAULT 1,
    created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT uk_lojas_codigo UNIQUE (codigo),
    CONSTRAINT chk_lojas_limite_sangria CHECK (limite_sangria_sem_aprovacao_centavos >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE usuarios (
    id                  BINARY(16)   NOT NULL,
    loja_id             BINARY(16)   NOT NULL,
    nome                VARCHAR(100) NOT NULL,
    email               VARCHAR(100) NOT NULL,
    senha_hash          VARCHAR(255) NOT NULL,
    pin_alcada_hash     VARCHAR(255) NULL,
    role                 VARCHAR(20) NOT NULL,
    ativo               TINYINT(1)   NOT NULL DEFAULT 1,
    ultimo_login_em     DATETIME(3)  NULL,
    versao              INT          NOT NULL DEFAULT 1,
    created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by          BINARY(16)   NULL,
    updated_by          BINARY(16)   NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_usuarios_loja_email UNIQUE (loja_id, email),
    CONSTRAINT fk_usuarios_loja FOREIGN KEY (loja_id) REFERENCES lojas (id) ON DELETE RESTRICT,
    CONSTRAINT chk_usuarios_role CHECK (role IN ('ATENDENTE', 'COZINHA', 'GERENTE', 'ADMIN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE refresh_tokens (
    id                  BINARY(16)   NOT NULL,
    loja_id             BINARY(16)   NOT NULL,
    usuario_id          BINARY(16)   NOT NULL,
    token_hash          VARCHAR(128) NOT NULL,
    dispositivo         VARCHAR(80)  NOT NULL,
    expira_em           DATETIME(3)  NOT NULL,
    revogado_em         DATETIME(3)  NULL,
    created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_loja FOREIGN KEY (loja_id) REFERENCES lojas (id) ON DELETE RESTRICT,
    CONSTRAINT fk_refresh_tokens_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id) ON DELETE RESTRICT,
    INDEX idx_refresh_tokens_usuario (usuario_id, revogado_em)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE audit_log (
    id                  BINARY(16)   NOT NULL,
    loja_id             BINARY(16)   NOT NULL,
    usuario_id          BINARY(16)   NULL,
    acao                VARCHAR(60)  NOT NULL,
    recurso             VARCHAR(60)  NOT NULL,
    recurso_id          BINARY(16)   NULL,
    ip                  VARCHAR(45)  NULL,
    dispositivo         VARCHAR(80)  NULL,
    detalhes_json       JSON         NULL,
    created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_audit_log_loja FOREIGN KEY (loja_id) REFERENCES lojas (id) ON DELETE RESTRICT,
    INDEX idx_audit_log_loja_data (loja_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------------
-- 2. MÓDULO DE CATÁLOGO (CATEGORIAS, PRODUTOS, MODIFICADORES, COMBOS, INSUMOS)
-- ---------------------------------------------------------------------------------
CREATE TABLE catalogo_versao (
    loja_id             BINARY(16)   NOT NULL,
    versao              BIGINT       NOT NULL DEFAULT 1,
    atualizado_em       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (loja_id),
    CONSTRAINT fk_catalogo_versao_loja FOREIGN KEY (loja_id) REFERENCES lojas (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE categorias (
    id                      BINARY(16)   NOT NULL,
    loja_id                 BINARY(16)   NOT NULL,
    parent_id               BINARY(16)   NULL,
    nome                    VARCHAR(60)  NOT NULL,
    slug                    VARCHAR(60)  NOT NULL,
    ordem                   INT          NOT NULL DEFAULT 0,
    ativo                   TINYINT(1)   NOT NULL DEFAULT 1,
    estacao_preparo_padrao  VARCHAR(15)  NOT NULL DEFAULT 'COZINHA',
    arquivado_em            DATETIME(3)  NULL,
    versao                  INT          NOT NULL DEFAULT 1,
    created_at              DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at              DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by              BINARY(16)   NULL,
    updated_by              BINARY(16)   NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_categorias_loja FOREIGN KEY (loja_id) REFERENCES lojas (id) ON DELETE RESTRICT,
    CONSTRAINT fk_categorias_parent FOREIGN KEY (parent_id) REFERENCES categorias (id) ON DELETE RESTRICT,
    CONSTRAINT chk_categorias_estacao CHECK (estacao_preparo_padrao IN ('BAR', 'COZINHA', 'NENHUMA')),
    INDEX idx_categorias_loja_slug (loja_id, parent_id, slug),
    INDEX idx_categorias_loja_ordem (loja_id, parent_id, ordem)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE produtos (
    id                  BINARY(16)    NOT NULL,
    loja_id             BINARY(16)    NOT NULL,
    categoria_id        BINARY(16)    NOT NULL,
    sku                 VARCHAR(30)   NOT NULL,
    nome                VARCHAR(80)   NOT NULL,
    descricao           VARCHAR(500)  NULL,
    imagem_url          VARCHAR(500)  NULL,
    tipo                VARCHAR(15)   NOT NULL DEFAULT 'SIMPLES',
    preco_base_centavos BIGINT        NOT NULL,
    estacao_preparo     VARCHAR(15)   NULL,
    ativo               TINYINT(1)    NOT NULL DEFAULT 1,
    pausado             TINYINT(1)    NOT NULL DEFAULT 0,
    motivo_pausa        VARCHAR(200)  NULL,
    ordem               INT           NOT NULL DEFAULT 0,
    arquivado_em        DATETIME(3)   NULL,
    versao              INT           NOT NULL DEFAULT 1,
    created_at          DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by          BINARY(16)    NULL,
    updated_by          BINARY(16)    NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_produtos_loja_sku UNIQUE (loja_id, sku),
    CONSTRAINT fk_produtos_loja FOREIGN KEY (loja_id) REFERENCES lojas (id) ON DELETE RESTRICT,
    CONSTRAINT fk_produtos_categoria FOREIGN KEY (categoria_id) REFERENCES categorias (id) ON DELETE RESTRICT,
    CONSTRAINT chk_produtos_tipo CHECK (tipo IN ('SIMPLES', 'COMBO')),
    CONSTRAINT chk_produtos_preco CHECK (preco_base_centavos >= 0),
    CONSTRAINT chk_produtos_estacao CHECK (estacao_preparo IS NULL OR estacao_preparo IN ('BAR', 'COZINHA', 'NENHUMA')),
    INDEX idx_produtos_loja_categoria (loja_id, categoria_id, ordem)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE grupos_modificadores (
    id                  BINARY(16)   NOT NULL,
    loja_id             BINARY(16)   NOT NULL,
    nome                VARCHAR(60)  NOT NULL,
    nome_interno        VARCHAR(60)  NULL,
    min_selecao         INT          NOT NULL DEFAULT 0,
    max_selecao         INT          NOT NULL DEFAULT 1,
    permite_repeticao   TINYINT(1)   NOT NULL DEFAULT 0,
    ordem               INT          NOT NULL DEFAULT 0,
    ativo               TINYINT(1)   NOT NULL DEFAULT 1,
    arquivado_em        DATETIME(3)  NULL,
    versao              INT          NOT NULL DEFAULT 1,
    created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by          BINARY(16)   NULL,
    updated_by          BINARY(16)   NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_grupos_mod_loja FOREIGN KEY (loja_id) REFERENCES lojas (id) ON DELETE RESTRICT,
    CONSTRAINT chk_grupos_mod_limites CHECK (min_selecao >= 0 AND max_selecao >= min_selecao AND max_selecao >= 1),
    INDEX idx_grupos_mod_loja (loja_id, ativo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE modificadores (
    id                  BINARY(16)   NOT NULL,
    loja_id             BINARY(16)   NOT NULL,
    grupo_id            BINARY(16)   NOT NULL,
    nome                VARCHAR(60)  NOT NULL,
    tipo_preco          VARCHAR(15)  NOT NULL DEFAULT 'NENHUM',
    valor_preco         BIGINT       NOT NULL DEFAULT 0,
    padrao              TINYINT(1)   NOT NULL DEFAULT 0,
    max_quantidade      INT          NOT NULL DEFAULT 1,
    ordem               INT          NOT NULL DEFAULT 0,
    ativo               TINYINT(1)   NOT NULL DEFAULT 1,
    pausado             TINYINT(1)   NOT NULL DEFAULT 0,
    motivo_pausa        VARCHAR(200) NULL,
    arquivado_em        DATETIME(3)  NULL,
    versao              INT          NOT NULL DEFAULT 1,
    created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by          BINARY(16)   NULL,
    updated_by          BINARY(16)   NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_modificadores_grupo_nome UNIQUE (grupo_id, nome),
    CONSTRAINT fk_modificadores_loja FOREIGN KEY (loja_id) REFERENCES lojas (id) ON DELETE RESTRICT,
    CONSTRAINT fk_modificadores_grupo FOREIGN KEY (grupo_id) REFERENCES grupos_modificadores (id) ON DELETE RESTRICT,
    CONSTRAINT chk_modificadores_tipo_preco CHECK (tipo_preco IN ('NENHUM', 'FIXO', 'PERCENTUAL')),
    CONSTRAINT chk_modificadores_valor_preco CHECK (valor_preco >= -10000),
    CONSTRAINT chk_modificadores_max_qtd CHECK (max_quantidade >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE produto_grupos_modificadores (
    produto_id           BINARY(16)  NOT NULL,
    grupo_id             BINARY(16)  NOT NULL,
    loja_id              BINARY(16)  NOT NULL,
    ordem                INT         NOT NULL DEFAULT 0,
    min_selecao_override INT         NULL,
    max_selecao_override INT         NULL,
    created_at           DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_by           BINARY(16)  NULL,
    PRIMARY KEY (produto_id, grupo_id),
    CONSTRAINT fk_prod_grupo_produto FOREIGN KEY (produto_id) REFERENCES produtos (id) ON DELETE CASCADE,
    CONSTRAINT fk_prod_grupo_grupo FOREIGN KEY (grupo_id) REFERENCES grupos_modificadores (id) ON DELETE RESTRICT,
    CONSTRAINT fk_prod_grupo_loja FOREIGN KEY (loja_id) REFERENCES lojas (id) ON DELETE RESTRICT,
    CONSTRAINT chk_prod_grupo_overrides CHECK (
        (min_selecao_override IS NULL OR min_selecao_override >= 0) AND
        (max_selecao_override IS NULL OR max_selecao_override >= 1) AND
        (min_selecao_override IS NULL OR max_selecao_override IS NULL OR max_selecao_override >= min_selecao_override)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE insumos (
    id                          BINARY(16)   NOT NULL,
    loja_id                     BINARY(16)   NOT NULL,
    nome                        VARCHAR(60)  NOT NULL,
    unidade                     VARCHAR(10)  NULL,
    quantidade_atual            BIGINT       NOT NULL DEFAULT 0,
    estoque_minimo              BIGINT       NOT NULL DEFAULT 0,
    disponivel                  TINYINT(1)   NOT NULL DEFAULT 1,
    motivo_indisponibilidade    VARCHAR(200) NULL,
    versao                      INT          NOT NULL DEFAULT 1,
    created_at                  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at                  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by                  BINARY(16)   NULL,
    updated_by                  BINARY(16)   NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_insumos_loja_nome UNIQUE (loja_id, nome),
    CONSTRAINT fk_insumos_loja FOREIGN KEY (loja_id) REFERENCES lojas (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE produto_insumos (
    produto_id          BINARY(16)  NOT NULL,
    insumo_id           BINARY(16)  NOT NULL,
    loja_id             BINARY(16)  NOT NULL,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_by          BINARY(16)  NULL,
    PRIMARY KEY (produto_id, insumo_id),
    CONSTRAINT fk_prod_insumo_produto FOREIGN KEY (produto_id) REFERENCES produtos (id) ON DELETE CASCADE,
    CONSTRAINT fk_prod_insumo_insumo FOREIGN KEY (insumo_id) REFERENCES insumos (id) ON DELETE RESTRICT,
    CONSTRAINT fk_prod_insumo_loja FOREIGN KEY (loja_id) REFERENCES lojas (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE modificador_insumos (
    modificador_id      BINARY(16)  NOT NULL,
    insumo_id           BINARY(16)  NOT NULL,
    loja_id             BINARY(16)  NOT NULL,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_by          BINARY(16)  NULL,
    PRIMARY KEY (modificador_id, insumo_id),
    CONSTRAINT fk_mod_insumo_modificador FOREIGN KEY (modificador_id) REFERENCES modificadores (id) ON DELETE CASCADE,
    CONSTRAINT fk_mod_insumo_insumo FOREIGN KEY (insumo_id) REFERENCES insumos (id) ON DELETE RESTRICT,
    CONSTRAINT fk_mod_insumo_loja FOREIGN KEY (loja_id) REFERENCES lojas (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE combo_slots (
    id                  BINARY(16)   NOT NULL,
    loja_id             BINARY(16)   NOT NULL,
    combo_produto_id    BINARY(16)   NOT NULL,
    nome                VARCHAR(60)  NOT NULL,
    min_selecao         INT          NOT NULL DEFAULT 1,
    max_selecao         INT          NOT NULL DEFAULT 1,
    ordem               INT          NOT NULL DEFAULT 0,
    created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_combo_slots_loja FOREIGN KEY (loja_id) REFERENCES lojas (id) ON DELETE RESTRICT,
    CONSTRAINT fk_combo_slots_produto FOREIGN KEY (combo_produto_id) REFERENCES produtos (id) ON DELETE CASCADE,
    CONSTRAINT chk_combo_slots_limites CHECK (min_selecao >= 0 AND max_selecao >= min_selecao AND max_selecao >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE combo_slot_opcoes (
    slot_id             BINARY(16)  NOT NULL,
    produto_id          BINARY(16)  NOT NULL,
    loja_id             BINARY(16)  NOT NULL,
    acrescimo_centavos  BIGINT      NOT NULL DEFAULT 0,
    padrao              TINYINT(1)  NOT NULL DEFAULT 0,
    ordem               INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (slot_id, produto_id),
    CONSTRAINT fk_combo_opcoes_slot FOREIGN KEY (slot_id) REFERENCES combo_slots (id) ON DELETE CASCADE,
    CONSTRAINT fk_combo_opcoes_produto FOREIGN KEY (produto_id) REFERENCES produtos (id) ON DELETE RESTRICT,
    CONSTRAINT fk_combo_opcoes_loja FOREIGN KEY (loja_id) REFERENCES lojas (id) ON DELETE RESTRICT,
    CONSTRAINT chk_combo_opcoes_acrescimo CHECK (acrescimo_centavos >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE historico_precos (
    id                  BINARY(16)   NOT NULL,
    loja_id             BINARY(16)   NOT NULL,
    entidade_tipo       VARCHAR(15)  NOT NULL,
    entidade_id         BINARY(16)   NOT NULL,
    tipo_preco_anterior VARCHAR(15)  NULL,
    valor_anterior      BIGINT       NULL,
    tipo_preco_novo     VARCHAR(15)  NOT NULL,
    valor_novo          BIGINT       NOT NULL,
    motivo              VARCHAR(200) NOT NULL,
    vigente_desde       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_by          BINARY(16)   NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_hist_precos_loja FOREIGN KEY (loja_id) REFERENCES lojas (id) ON DELETE RESTRICT,
    CONSTRAINT chk_hist_precos_entidade CHECK (entidade_tipo IN ('PRODUTO', 'MODIFICADOR')),
    INDEX idx_hist_precos_entidade (loja_id, entidade_tipo, entidade_id, vigente_desde DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------------
-- 3. MÓDULO DE PEDIDOS & EVENTOS KDS (OUTBOX)
-- ---------------------------------------------------------------------------------
CREATE TABLE pedidos (
    id                      BINARY(16)   NOT NULL,
    loja_id                 BINARY(16)   NOT NULL,
    sessao_caixa_id         BINARY(16)   NULL,
    cliente_id              BINARY(16)   NULL,
    idempotency_key         BINARY(16)   NOT NULL,
    numero_diario           INT          NOT NULL,
    data_operacional        DATE         NOT NULL,
    origem                  VARCHAR(20)  NOT NULL DEFAULT 'APP_ATENDENTE',
    tipo_atendimento        VARCHAR(20)  NOT NULL DEFAULT 'BALCAO',
    modalidade_pagamento    VARCHAR(15)  NOT NULL DEFAULT 'PRE_PAGO',
    identificador_cliente   VARCHAR(60)  NULL,
    mesa                    VARCHAR(10)  NULL,
    observacao              VARCHAR(300) NULL,
    status                  VARCHAR(25)  NOT NULL DEFAULT 'CRIADO',
    subtotal_centavos       BIGINT       NOT NULL,
    desconto_centavos       BIGINT       NOT NULL DEFAULT 0,
    total_centavos          BIGINT       NOT NULL,
    total_pago_centavos     BIGINT       NOT NULL DEFAULT 0,
    saldo_devedor_centavos  BIGINT       NOT NULL,
    motivo_cancelamento     VARCHAR(200) NULL,
    cancelado_por           BINARY(16)   NULL,
    registrar_perda         TINYINT(1)   NOT NULL DEFAULT 0,
    fiscal_status           VARCHAR(20)  NULL,
    confirmado_em           DATETIME(3)  NULL,
    preparo_iniciado_em     DATETIME(3)  NULL,
    pronto_em               DATETIME(3)  NULL,
    entregue_em             DATETIME(3)  NULL,
    versao                  INT          NOT NULL DEFAULT 1,
    created_at              DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at              DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by              BINARY(16)   NULL,
    updated_by              BINARY(16)   NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_pedidos_loja_idempotency UNIQUE (loja_id, idempotency_key),
    CONSTRAINT uk_pedidos_loja_data_numero UNIQUE (loja_id, data_operacional, numero_diario),
    CONSTRAINT fk_pedidos_loja FOREIGN KEY (loja_id) REFERENCES lojas (id) ON DELETE RESTRICT,
    CONSTRAINT chk_pedidos_origem CHECK (origem IN ('APP_ATENDENTE', 'MASTER_BALCAO')),
    CONSTRAINT chk_pedidos_tipo_atendimento CHECK (tipo_atendimento IN ('MESA', 'BALCAO', 'DELIVERY')),
    CONSTRAINT chk_pedidos_modalidade CHECK (modalidade_pagamento IN ('PRE_PAGO', 'POS_PAGO')),
    CONSTRAINT chk_pedidos_status CHECK (status IN ('CRIADO', 'AGUARDANDO_PAGAMENTO', 'CONFIRMADO', 'EM_PREPARO', 'PRONTO', 'ENTREGUE', 'CANCELADO', 'ESTORNADO')),
    CONSTRAINT chk_pedidos_valores CHECK (
        subtotal_centavos >= 0 AND
        desconto_centavos >= 0 AND
        total_centavos = subtotal_centavos - desconto_centavos AND
        total_centavos >= 0 AND
        total_pago_centavos >= 0 AND
        saldo_devedor_centavos >= 0
    ),
    INDEX idx_pedidos_loja_status (loja_id, status, confirmado_em),
    INDEX idx_pedidos_loja_data (loja_id, data_operacional)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE itens_pedido (
    id                              BINARY(16)   NOT NULL,
    loja_id                         BINARY(16)   NOT NULL,
    pedido_id                       BINARY(16)   NOT NULL,
    produto_id                      BINARY(16)   NOT NULL,
    item_pai_id                     BINARY(16)   NULL,
    combo_slot_id                   BINARY(16)   NULL,
    combo_slot_nome                 VARCHAR(60)  NULL,
    produto_nome                    VARCHAR(80)  NOT NULL,
    preco_base_centavos             BIGINT       NOT NULL,
    quantidade                      INT          NOT NULL,
    preco_unitario_final_centavos   BIGINT       NOT NULL,
    total_centavos                  BIGINT       NOT NULL,
    estacao_preparo                 VARCHAR(15)  NOT NULL,
    observacao                      VARCHAR(200) NULL,
    created_at                      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_itens_pedido_loja FOREIGN KEY (loja_id) REFERENCES lojas (id) ON DELETE RESTRICT,
    CONSTRAINT fk_itens_pedido_pedido FOREIGN KEY (pedido_id) REFERENCES pedidos (id) ON DELETE RESTRICT,
    CONSTRAINT fk_itens_pedido_produto FOREIGN KEY (produto_id) REFERENCES produtos (id) ON DELETE RESTRICT,
    CONSTRAINT fk_itens_pedido_pai FOREIGN KEY (item_pai_id) REFERENCES itens_pedido (id) ON DELETE RESTRICT,
    CONSTRAINT chk_itens_pedido_qtd CHECK (quantidade > 0),
    CONSTRAINT chk_itens_pedido_valores CHECK (preco_base_centavos >= 0 AND preco_unitario_final_centavos >= 0 AND total_centavos >= 0),
    INDEX idx_itens_pedido_pedido (pedido_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE itens_pedido_modificadores (
    id                          BINARY(16)  NOT NULL,
    loja_id                     BINARY(16)  NOT NULL,
    item_pedido_id              BINARY(16)  NOT NULL,
    modificador_id              BINARY(16)  NOT NULL,
    grupo_nome                  VARCHAR(60) NOT NULL,
    modificador_nome            VARCHAR(60) NOT NULL,
    tipo_preco                  VARCHAR(15) NOT NULL,
    valor_preco                 BIGINT      NOT NULL,
    quantidade                  INT         NOT NULL,
    valor_calculado_centavos    BIGINT      NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_item_mod_unico UNIQUE (item_pedido_id, modificador_id),
    CONSTRAINT fk_item_mod_loja FOREIGN KEY (loja_id) REFERENCES lojas (id) ON DELETE RESTRICT,
    CONSTRAINT fk_item_mod_item FOREIGN KEY (item_pedido_id) REFERENCES itens_pedido (id) ON DELETE RESTRICT,
    CONSTRAINT fk_item_mod_modificador FOREIGN KEY (modificador_id) REFERENCES modificadores (id) ON DELETE RESTRICT,
    CONSTRAINT chk_item_mod_qtd CHECK (quantidade >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE pedido_eventos (
    seq                 BIGINT       NOT NULL AUTO_INCREMENT,
    id                  BINARY(16)   NOT NULL,
    loja_id             BINARY(16)   NOT NULL,
    pedido_id           BINARY(16)   NOT NULL,
    tipo                VARCHAR(30)  NOT NULL,
    status_anterior     VARCHAR(25)  NULL,
    status_novo         VARCHAR(25)  NOT NULL,
    versao_pedido       INT          NOT NULL,
    motivo              VARCHAR(200) NULL,
    payload_json        TEXT         NOT NULL,
    created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_by          BINARY(16)   NULL,
    PRIMARY KEY (seq),
    CONSTRAINT uk_pedido_eventos_id UNIQUE (id),
    CONSTRAINT fk_pedido_eventos_loja FOREIGN KEY (loja_id) REFERENCES lojas (id) ON DELETE RESTRICT,
    CONSTRAINT fk_pedido_eventos_pedido FOREIGN KEY (pedido_id) REFERENCES pedidos (id) ON DELETE RESTRICT,
    INDEX idx_pedido_eventos_loja_seq (loja_id, seq)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------------
-- 4. MÓDULO FINANCEIRO E CAIXA RIGOROSO (IMUTÁVEL)
-- ---------------------------------------------------------------------------------
CREATE TABLE caixas (
    id                  BINARY(16)   NOT NULL,
    loja_id             BINARY(16)   NOT NULL,
    nome                VARCHAR(60)  NOT NULL,
    impressora_ip       VARCHAR(45)  NULL,
    impressora_porta    INT          NOT NULL DEFAULT 9100,
    possui_gaveta       TINYINT(1)   NOT NULL DEFAULT 1,
    ativo               TINYINT(1)   NOT NULL DEFAULT 1,
    versao              INT          NOT NULL DEFAULT 1,
    created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT uk_caixas_loja_nome UNIQUE (loja_id, nome),
    CONSTRAINT fk_caixas_loja FOREIGN KEY (loja_id) REFERENCES lojas (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Sessão de caixa: NÃO possui coluna de saldo dinâmico em tempo de operação.
-- As colunas esperado/declarado/diferenca são snapshots preenchidos UMA ÚNICA VEZ no fechamento.
-- Colunas geradas garantem no máximo 1 sessão ABERTA por caixa e por operador.
CREATE TABLE sessoes_caixa (
    id                          BINARY(16)   NOT NULL,
    loja_id                     BINARY(16)   NOT NULL,
    caixa_id                    BINARY(16)   NOT NULL,
    operador_id                 BINARY(16)   NOT NULL,
    protocolo                   VARCHAR(40)  NULL,
    status                      VARCHAR(15)  NOT NULL DEFAULT 'ABERTA',
    fundo_troco_centavos        BIGINT       NOT NULL DEFAULT 0,
    aberta_em                   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    fechada_em                  DATETIME(3)  NULL,
    conferida_em                DATETIME(3)  NULL,
    conferida_por               BINARY(16)   NULL,
    esperado_dinheiro_centavos  BIGINT       NULL,
    declarado_dinheiro_centavos BIGINT       NULL,
    diferenca_dinheiro_centavos BIGINT       NULL,
    observacao_operador         VARCHAR(300) NULL,
    observacao_conferencia      VARCHAR(500) NULL,
    versao                      INT          NOT NULL DEFAULT 1,
    created_at                  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at                  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by                  BINARY(16)   NULL,
    updated_by                  BINARY(16)   NULL,
    caixa_aberto_guard          BINARY(16)   GENERATED ALWAYS AS (IF(status = 'ABERTA', caixa_id, NULL)) STORED,
    operador_aberto_guard       BINARY(16)   GENERATED ALWAYS AS (IF(status = 'ABERTA', operador_id, NULL)) STORED,
    PRIMARY KEY (id),
    CONSTRAINT uk_sessoes_caixa_aberto UNIQUE (caixa_aberto_guard),
    CONSTRAINT uk_sessoes_operador_aberto UNIQUE (operador_aberto_guard),
    CONSTRAINT fk_sessoes_caixa_loja FOREIGN KEY (loja_id) REFERENCES lojas (id) ON DELETE RESTRICT,
    CONSTRAINT fk_sessoes_caixa_terminal FOREIGN KEY (caixa_id) REFERENCES caixas (id) ON DELETE RESTRICT,
    CONSTRAINT fk_sessoes_caixa_operador FOREIGN KEY (operador_id) REFERENCES usuarios (id) ON DELETE RESTRICT,
    CONSTRAINT chk_sessoes_caixa_status CHECK (status IN ('ABERTA', 'FECHADA', 'CONFERIDA')),
    CONSTRAINT chk_sessoes_fundo_troco CHECK (fundo_troco_centavos >= 0),
    INDEX idx_sessoes_loja_aberta (loja_id, aberta_em DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE pagamentos (
    id                      BINARY(16)   NOT NULL,
    loja_id                 BINARY(16)   NOT NULL,
    pedido_id               BINARY(16)   NOT NULL,
    sessao_caixa_id         BINARY(16)   NOT NULL,
    idempotency_key         BINARY(16)   NOT NULL,
    metodo                  VARCHAR(15)  NOT NULL,
    valor_centavos          BIGINT       NOT NULL,
    valor_recebido_centavos BIGINT       NULL,
    troco_centavos          BIGINT       NULL,
    status                  VARCHAR(15)  NOT NULL DEFAULT 'CONFIRMADO',
    nsu                     VARCHAR(40)  NULL,
    autorizacao             VARCHAR(40)  NULL,
    txid_pix                VARCHAR(64)  NULL,
    motivo_estorno          VARCHAR(200) NULL,
    estornado_em            DATETIME(3)  NULL,
    estornado_por           BINARY(16)   NULL,
    versao                  INT          NOT NULL DEFAULT 1,
    created_at              DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at              DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by              BINARY(16)   NULL,
    updated_by              BINARY(16)   NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_pagamentos_loja_idempotency UNIQUE (loja_id, idempotency_key),
    CONSTRAINT fk_pagamentos_loja FOREIGN KEY (loja_id) REFERENCES lojas (id) ON DELETE RESTRICT,
    CONSTRAINT fk_pagamentos_pedido FOREIGN KEY (pedido_id) REFERENCES pedidos (id) ON DELETE RESTRICT,
    CONSTRAINT fk_pagamentos_sessao FOREIGN KEY (sessao_caixa_id) REFERENCES sessoes_caixa (id) ON DELETE RESTRICT,
    CONSTRAINT chk_pagamentos_metodo CHECK (metodo IN ('DINHEIRO', 'PIX', 'CREDITO', 'DEBITO', 'VOUCHER')),
    CONSTRAINT chk_pagamentos_status CHECK (status IN ('CONFIRMADO', 'ESTORNADO')),
    CONSTRAINT chk_pagamentos_valores CHECK (
        valor_centavos > 0 AND
        (metodo <> 'DINHEIRO' OR (valor_recebido_centavos >= valor_centavos AND troco_centavos = valor_recebido_centavos - valor_centavos)) AND
        (metodo = 'DINHEIRO' OR (troco_centavos IS NULL OR troco_centavos = 0))
    ),
    INDEX idx_pagamentos_sessao_metodo (sessao_caixa_id, metodo, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Movimentações físicas de caixa: ESTRITAMENTE IMUTÁVEL (insert-only).
CREATE TABLE movimentacoes_caixa (
    id                          BINARY(16)   NOT NULL,
    loja_id                     BINARY(16)   NOT NULL,
    sessao_caixa_id             BINARY(16)   NOT NULL,
    idempotency_key             BINARY(16)   NOT NULL,
    tipo                        VARCHAR(30)  NOT NULL,
    natureza                    VARCHAR(10)  NOT NULL,
    valor_centavos              BIGINT       NOT NULL,
    motivo                      VARCHAR(200) NULL,
    pagamento_id                BINARY(16)   NULL,
    movimentacao_estornada_id   BINARY(16)   NULL,
    aprovado_por                BINARY(16)   NULL,
    sinalizacao                 VARCHAR(40)  NULL,
    created_at                  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_by                  BINARY(16)   NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_mov_caixa_loja_idempotency UNIQUE (loja_id, idempotency_key),
    CONSTRAINT uk_mov_caixa_estornada UNIQUE (movimentacao_estornada_id),
    CONSTRAINT fk_mov_caixa_loja FOREIGN KEY (loja_id) REFERENCES lojas (id) ON DELETE RESTRICT,
    CONSTRAINT fk_mov_caixa_sessao FOREIGN KEY (sessao_caixa_id) REFERENCES sessoes_caixa (id) ON DELETE RESTRICT,
    CONSTRAINT fk_mov_caixa_pagamento FOREIGN KEY (pagamento_id) REFERENCES pagamentos (id) ON DELETE RESTRICT,
    CONSTRAINT fk_mov_caixa_estornada FOREIGN KEY (movimentacao_estornada_id) REFERENCES movimentacoes_caixa (id) ON DELETE RESTRICT,
    CONSTRAINT chk_mov_caixa_tipo CHECK (tipo IN ('ABERTURA', 'VENDA', 'VENDA_DINHEIRO', 'SANGRIA', 'SUPRIMENTO', 'ESTORNO', 'ESTORNO_VENDA_DINHEIRO', 'ESTORNO_MOVIMENTACAO')),
    CONSTRAINT chk_mov_caixa_natureza CHECK (natureza IN ('ENTRADA', 'SAIDA')),
    CONSTRAINT chk_mov_caixa_valor CHECK (valor_centavos >= 0),
    CONSTRAINT chk_mov_caixa_motivo CHECK (
        tipo NOT IN ('SANGRIA', 'SUPRIMENTO', 'ESTORNO_VENDA_DINHEIRO', 'ESTORNO_MOVIMENTACAO')
        OR (motivo IS NOT NULL AND CHAR_LENGTH(TRIM(motivo)) >= 5)
    ),
    INDEX idx_mov_caixa_sessao (sessao_caixa_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE fechamento_declaracoes (
    id                          BINARY(16)  NOT NULL,
    loja_id                     BINARY(16)  NOT NULL,
    sessao_caixa_id             BINARY(16)  NOT NULL,
    metodo                      VARCHAR(15) NOT NULL,
    valor_declarado_centavos    BIGINT      NOT NULL,
    valor_esperado_centavos     BIGINT      NOT NULL,
    diferenca_centavos          BIGINT      NOT NULL,
    quantidade_comprovantes     INT         NULL,
    created_at                  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_by                  BINARY(16)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_fechamento_decl_sessao_metodo UNIQUE (sessao_caixa_id, metodo),
    CONSTRAINT fk_fechamento_decl_loja FOREIGN KEY (loja_id) REFERENCES lojas (id) ON DELETE RESTRICT,
    CONSTRAINT fk_fechamento_decl_sessao FOREIGN KEY (sessao_caixa_id) REFERENCES sessoes_caixa (id) ON DELETE RESTRICT,
    CONSTRAINT chk_fechamento_decl_valor CHECK (valor_declarado_centavos >= 0 AND valor_esperado_centavos >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------------
-- 5. TRIGGERS DE IMUTABILIDADE CONTÁBIL E DE AUDITORIA
-- ---------------------------------------------------------------------------------
DELIMITER $$

CREATE TRIGGER trg_movimentacoes_caixa_no_update
BEFORE UPDATE ON movimentacoes_caixa
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'VIOLACAO_IMUTABILIDADE: movimentacoes_caixa e imutavel. Utilize lancamento reverso (ESTORNO_MOVIMENTACAO).';
END$$

CREATE TRIGGER trg_movimentacoes_caixa_no_delete
BEFORE DELETE ON movimentacoes_caixa
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'VIOLACAO_IMUTABILIDADE: movimentacoes_caixa nao permite exclusao fisica.';
END$$

CREATE TRIGGER trg_fechamento_declaracoes_no_update
BEFORE UPDATE ON fechamento_declaracoes
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'VIOLACAO_IMUTABILIDADE: fechamento_declaracoes e imutavel apos declaracao.';
END$$

CREATE TRIGGER trg_fechamento_declaracoes_no_delete
BEFORE DELETE ON fechamento_declaracoes
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'VIOLACAO_IMUTABILIDADE: fechamento_declaracoes nao permite exclusao fisica.';
END$$

CREATE TRIGGER trg_pedido_eventos_no_update
BEFORE UPDATE ON pedido_eventos
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'VIOLACAO_IMUTABILIDADE: pedido_eventos (outbox/auditoria) e imutavel.';
END$$

CREATE TRIGGER trg_pedido_eventos_no_delete
BEFORE DELETE ON pedido_eventos
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'VIOLACAO_IMUTABILIDADE: pedido_eventos nao permite exclusao fisica.';
END$$

CREATE TRIGGER trg_historico_precos_no_update
BEFORE UPDATE ON historico_precos
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'VIOLACAO_IMUTABILIDADE: historico_precos e imutavel.';
END$$

CREATE TRIGGER trg_historico_precos_no_delete
BEFORE DELETE ON historico_precos
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'VIOLACAO_IMUTABILIDADE: historico_precos nao permite exclusao fisica.';
END$$

CREATE TRIGGER trg_sessoes_caixa_protect_snapshot
BEFORE UPDATE ON sessoes_caixa
FOR EACH ROW
BEGIN
    IF OLD.status = 'CONFERIDA' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'VIOLACAO_IMUTABILIDADE: sessao de caixa ja CONFERIDA nao pode ser alterada.';
    END IF;
    IF OLD.status = 'FECHADA' AND (
        OLD.esperado_dinheiro_centavos <> NEW.esperado_dinheiro_centavos OR
        OLD.declarado_dinheiro_centavos <> NEW.declarado_dinheiro_centavos OR
        OLD.diferenca_dinheiro_centavos <> NEW.diferenca_dinheiro_centavos OR
        OLD.fundo_troco_centavos <> NEW.fundo_troco_centavos
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'VIOLACAO_IMUTABILIDADE: valores de snapshot do fechamento cego sao imutaveis.';
    END IF;
END$$

CREATE TRIGGER trg_sessoes_caixa_no_delete
BEFORE DELETE ON sessoes_caixa
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'VIOLACAO_IMUTABILIDADE: sessoes_caixa nao permite exclusao fisica.';
END$$

DELIMITER ;
