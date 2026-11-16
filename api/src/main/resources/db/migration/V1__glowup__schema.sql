-- GlowUp | Modelo fisico integrado - PostgreSQL 14+
-- Versao 1: feito com base nos diagramas MER/DER e requisitos descritos no documento.

BEGIN;

-- 1. USUARIOS E SEGURANCA
CREATE TABLE usuarios (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nome VARCHAR(100) NOT NULL,
    data_nascimento DATE NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    senha_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'CLIENT',
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT ck_usuarios_role CHECK (role IN ('CLIENT','ADMIN')),
    CONSTRAINT ck_usuarios_soft_delete CHECK ((ativo AND deleted_at IS NULL) OR (NOT ativo AND deleted_at IS NOT NULL))
);

CREATE TABLE enderecos (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE RESTRICT,
    pais VARCHAR(60) NOT NULL DEFAULT 'Brasil',
    cep VARCHAR(10) NOT NULL,
    logradouro VARCHAR(180) NOT NULL,
    numero VARCHAR(30) NOT NULL,
    complemento VARCHAR(120),
    bairro VARCHAR(100) NOT NULL,
    cidade VARCHAR(100) NOT NULL,
    uf CHAR(2) NOT NULL,
    principal BOOLEAN NOT NULL DEFAULT FALSE,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT ck_enderecos_uf CHECK (uf = UPPER(uf)),
    CONSTRAINT ck_enderecos_soft_delete CHECK ((ativo AND deleted_at IS NULL) OR (NOT ativo AND deleted_at IS NOT NULL))
);
CREATE UNIQUE INDEX uq_endereco_principal_usuario ON enderecos(usuario_id) WHERE principal AND ativo AND deleted_at IS NULL;

CREATE TABLE telefones (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE RESTRICT,
    numero VARCHAR(20) NOT NULL,
    principal BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_telefone_usuario_numero UNIQUE (usuario_id, numero)
);
CREATE UNIQUE INDEX uq_telefone_principal_usuario ON telefones(usuario_id) WHERE principal;

CREATE TABLE termos_aceites (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE RESTRICT,
    tipo_aceite VARCHAR(50) NOT NULL,
    versao_documento VARCHAR(20) NOT NULL,
    data_hora_aceite TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE recuperacoes_senha (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE RESTRICT,
    codigo_otp_hash VARCHAR(255) NOT NULL,
    expiracao TIMESTAMPTZ NOT NULL,
    utilizado BOOLEAN NOT NULL DEFAULT FALSE,
    criado_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    utilizado_at TIMESTAMPTZ
);

-- 2. CATALOGO, CATEGORIAS, PRODUTOS E KITS
CREATE TABLE categorias (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nome VARCHAR(100) NOT NULL UNIQUE,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT ck_categorias_soft_delete CHECK ((ativo AND deleted_at IS NULL) OR (NOT ativo AND deleted_at IS NOT NULL))
);

CREATE TABLE produtos (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    categoria_id BIGINT NOT NULL REFERENCES categorias(id) ON DELETE RESTRICT,
    nome VARCHAR(180) NOT NULL,
    descricao TEXT,
    marca VARCHAR(80) NOT NULL,
    custo_fabrica NUMERIC(12,2) NOT NULL,
    preco_base NUMERIC(12,2) NOT NULL,
    preco_promocional NUMERIC(12,2),
    data_inicio_oferta TIMESTAMPTZ,
    data_fim_promocao TIMESTAMPTZ,
    modalidade VARCHAR(20) NOT NULL,
    tipo_kit VARCHAR(20),
    tempo_medio_uso_dias INTEGER,
    imagem_url TEXT,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT ck_produtos_modalidade CHECK (modalidade IN ('PRONTA_ENTREGA','SOB_ENCOMENDA')),
    CONSTRAINT ck_produtos_tipo_kit CHECK (tipo_kit IS NULL OR tipo_kit IN ('MANUAL','PRONTO')),
    CONSTRAINT ck_produtos_valores CHECK (custo_fabrica >= 0 AND preco_base >= 0 AND (preco_promocional IS NULL OR (preco_promocional >= 0 AND preco_promocional <= preco_base))),
    CONSTRAINT ck_produtos_promocao CHECK ((preco_promocional IS NULL AND data_inicio_oferta IS NULL AND data_fim_promocao IS NULL) OR (preco_promocional IS NOT NULL AND data_fim_promocao IS NOT NULL AND (data_inicio_oferta IS NULL OR data_fim_promocao > data_inicio_oferta))),
    CONSTRAINT ck_produtos_tempo_uso CHECK (tempo_medio_uso_dias IS NULL OR tempo_medio_uso_dias > 0),
    CONSTRAINT ck_produtos_soft_delete CHECK ((ativo AND deleted_at IS NULL) OR (NOT ativo AND deleted_at IS NOT NULL))
);

-- Tabela de associacao: um produto do tipo kit e composto por produtos componentes.
CREATE TABLE kit_itens (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    produto_kit_id BIGINT NOT NULL REFERENCES produtos(id) ON DELETE RESTRICT,
    produto_componente_id BIGINT NOT NULL REFERENCES produtos(id) ON DELETE RESTRICT,
    quantidade INTEGER NOT NULL DEFAULT 1,
    CONSTRAINT uq_kit_componente UNIQUE (produto_kit_id, produto_componente_id),
    CONSTRAINT ck_kit_item_nao_auto CHECK (produto_kit_id <> produto_componente_id),
    CONSTRAINT ck_kit_item_quantidade CHECK (quantidade > 0)
);

-- 3. ESTOQUE, RESERVAS, MOVIMENTACOES E ALERTAS
CREATE TABLE estoques (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    produto_id BIGINT NOT NULL UNIQUE REFERENCES produtos(id) ON DELETE RESTRICT,
    quantidade INTEGER NOT NULL DEFAULT 0,
    quantidade_reservada INTEGER NOT NULL DEFAULT 0,
    quantidade_minima INTEGER NOT NULL DEFAULT 0,
    data_validade DATE,
    atualizado_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_estoque_quantidades CHECK (quantidade >= 0 AND quantidade_reservada >= 0 AND quantidade_reservada <= quantidade AND quantidade_minima >= 0)
);

CREATE TABLE reservas_estoque (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pedido_id BIGINT NOT NULL,
    estoque_id BIGINT NOT NULL REFERENCES estoques(id) ON DELETE RESTRICT,
    quantidade INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'RESERVADA',
    criada_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expira_at TIMESTAMPTZ,
    consumida_at TIMESTAMPTZ,
    liberada_at TIMESTAMPTZ,
    CONSTRAINT ck_reserva_quantidade CHECK (quantidade > 0),
    CONSTRAINT ck_reserva_status CHECK (status IN ('RESERVADA','CONSUMIDA','LIBERADA','EXPIRADA'))
);

CREATE TABLE movimentacoes_estoque (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    estoque_id BIGINT NOT NULL REFERENCES estoques(id) ON DELETE RESTRICT,
    pedido_id BIGINT,
    item_devolucao_id BIGINT,
    tipo VARCHAR(25) NOT NULL,
    quantidade INTEGER NOT NULL,
    data_movimentacao TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    observacao TEXT,
    CONSTRAINT ck_movimentacao_tipo CHECK (tipo IN ('ENTRADA','SAIDA','RESERVA','LIBERACAO','REINTEGRACAO','AJUSTE')),
    CONSTRAINT ck_movimentacao_quantidade CHECK (quantidade > 0)
);

CREATE TABLE alertas_validade (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    estoque_id BIGINT NOT NULL REFERENCES estoques(id) ON DELETE RESTRICT,
    data_prevista DATE NOT NULL,
    dias_restantes INTEGER NOT NULL,
    mensagem TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ABERTO',
    criado_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolvido_at TIMESTAMPTZ,
    CONSTRAINT ck_alerta_status CHECK (status IN ('ABERTO','VISUALIZADO','RESOLVIDO'))
);

-- 4. CAMINHOS / LOGISTICA
CREATE TABLE caminhos (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nome VARCHAR(100) NOT NULL,
    descricao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE itens_caminho (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    caminho_id BIGINT NOT NULL REFERENCES caminhos(id) ON DELETE RESTRICT,
    descricao VARCHAR(255) NOT NULL,
    ordem INTEGER NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_item_caminho_ordem UNIQUE (caminho_id, ordem),
    CONSTRAINT ck_item_caminho_ordem CHECK (ordem > 0)
);

-- 5. CUPONS, CARRINHO E PEDIDOS
CREATE TABLE cupons (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    codigo VARCHAR(50) NOT NULL UNIQUE,
    percentual_desconto NUMERIC(5,2),
    valor_desconto NUMERIC(12,2),
    valor_minimo NUMERIC(12,2) NOT NULL DEFAULT 0,
    data_inicio TIMESTAMPTZ NOT NULL,
    data_fim TIMESTAMPTZ NOT NULL,
    limite_uso INTEGER,
    usos_realizados INTEGER NOT NULL DEFAULT 0,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_cupom_desconto_exclusivo CHECK ((percentual_desconto IS NOT NULL AND valor_desconto IS NULL) OR (percentual_desconto IS NULL AND valor_desconto IS NOT NULL)),
    CONSTRAINT ck_cupom_valores CHECK (COALESCE(percentual_desconto, valor_desconto) > 0 AND (percentual_desconto IS NULL OR percentual_desconto <= 100) AND valor_minimo >= 0),
    CONSTRAINT ck_cupom_datas CHECK (data_fim > data_inicio),
    CONSTRAINT ck_cupom_usos CHECK (usos_realizados >= 0 AND (limite_uso IS NULL OR (limite_uso >= 0 AND usos_realizados <= limite_uso)))
);

CREATE TABLE carrinhos (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE RESTRICT,
    identificador_sessao VARCHAR(150),
    status VARCHAR(20) NOT NULL DEFAULT 'ATIVO',
    criado_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expiracao TIMESTAMPTZ,
    CONSTRAINT ck_carrinho_status CHECK (status IN ('ATIVO','CONVERTIDO','ABANDONADO','EXPIRADO'))
);
CREATE UNIQUE INDEX uq_carrinho_ativo_usuario ON carrinhos(usuario_id) WHERE status = 'ATIVO';

CREATE TABLE itens_carrinho (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    carrinho_id BIGINT NOT NULL REFERENCES carrinhos(id) ON DELETE RESTRICT,
    produto_id BIGINT REFERENCES produtos(id) ON DELETE RESTRICT,
    quantidade INTEGER NOT NULL,
    preco_unitario NUMERIC(12,2) NOT NULL,
    criado_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_item_carrinho_quantidade CHECK (quantidade > 0),
    CONSTRAINT ck_item_carrinho_preco CHECK (preco_unitario >= 0),
    CONSTRAINT ck_item_carrinho_produto CHECK (produto_id IS NOT NULL)
);
CREATE UNIQUE INDEX uq_item_produto_carrinho ON itens_carrinho(carrinho_id, produto_id);

CREATE TABLE pedidos (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE RESTRICT,
    cupom_id BIGINT REFERENCES cupons(id) ON DELETE RESTRICT,
    status VARCHAR(25) NOT NULL DEFAULT 'PROCESSANDO',
    status_pagamento VARCHAR(30) NOT NULL DEFAULT 'PENDENTE',
    subtotal NUMERIC(12,2) NOT NULL,
    desconto NUMERIC(12,2) NOT NULL DEFAULT 0,
    frete NUMERIC(12,2) NOT NULL DEFAULT 0,
    total NUMERIC(12,2) NOT NULL,
    data_pedido TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_entrega TIMESTAMPTZ,
    prazo_dias INTEGER,
    atualizado_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_pedido_status CHECK (status IN ('PROCESSANDO','EM_SEPARACAO','ENVIADO','ENTREGUE','CANCELADO')),
    CONSTRAINT ck_pedido_status_pagamento CHECK (status_pagamento IN ('PENDENTE','APROVADO','RECUSADO','CANCELADO','ESTORNADO','PARCIALMENTE_ESTORNADO')),
    CONSTRAINT ck_pedido_valores CHECK (subtotal >= 0 AND desconto >= 0 AND frete >= 0 AND total >= 0 AND total = subtotal - desconto + frete)
);

ALTER TABLE reservas_estoque ADD CONSTRAINT fk_reserva_pedido FOREIGN KEY (pedido_id) REFERENCES pedidos(id) ON DELETE RESTRICT;
ALTER TABLE movimentacoes_estoque ADD CONSTRAINT fk_movimentacao_pedido FOREIGN KEY (pedido_id) REFERENCES pedidos(id) ON DELETE RESTRICT;

CREATE TABLE itens_pedido (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pedido_id BIGINT NOT NULL REFERENCES pedidos(id) ON DELETE RESTRICT,
    produto_id BIGINT NOT NULL REFERENCES produtos(id) ON DELETE RESTRICT,
    quantidade INTEGER NOT NULL,
    preco_praticado NUMERIC(12,2) NOT NULL,
    custo_fabrica NUMERIC(12,2) NOT NULL,
    subtotal NUMERIC(12,2) NOT NULL,
    CONSTRAINT ck_item_pedido_quantidade CHECK (quantidade > 0),
    CONSTRAINT ck_item_pedido_valores CHECK (preco_praticado >= 0 AND custo_fabrica >= 0 AND subtotal >= 0)
);

CREATE TABLE pedido_enderecos (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pedido_id BIGINT NOT NULL UNIQUE REFERENCES pedidos(id) ON DELETE RESTRICT,
    endereco_id BIGINT REFERENCES enderecos(id) ON DELETE RESTRICT,
    pais VARCHAR(60) NOT NULL DEFAULT 'Brasil',
    cep VARCHAR(10),
    logradouro VARCHAR(180),
    numero VARCHAR(30),
    complemento VARCHAR(120),
    bairro VARCHAR(100),
    cidade VARCHAR(100),
    uf CHAR(2),
    tipo_entrega VARCHAR(20) NOT NULL DEFAULT 'DOMICILIO',
    prazo_dias INTEGER,
    CONSTRAINT ck_pedido_endereco_tipo CHECK (tipo_entrega IN ('DOMICILIO','RETIRADA')),
    CONSTRAINT ck_pedido_endereco_dados CHECK (tipo_entrega = 'RETIRADA' OR (endereco_id IS NOT NULL OR (cep IS NOT NULL AND logradouro IS NOT NULL AND numero IS NOT NULL AND cidade IS NOT NULL AND uf IS NOT NULL)))
);

-- 6. PAGAMENTOS, PARCELAS, ESTORNOS E WEBHOOKS
CREATE TABLE pagamentos (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pedido_id BIGINT NOT NULL UNIQUE REFERENCES pedidos(id) ON DELETE RESTRICT,
    metodo VARCHAR(10) NOT NULL,
    status VARCHAR(25) NOT NULL DEFAULT 'PENDENTE',
    valor NUMERIC(12,2) NOT NULL,
    taxa NUMERIC(12,2) NOT NULL DEFAULT 0,
    juros NUMERIC(12,2) NOT NULL DEFAULT 0,
    provedor VARCHAR(50) NOT NULL DEFAULT 'MERCADO_PAGO',
    identificador_gateway VARCHAR(150) UNIQUE,
    criado_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_pagamento_metodo CHECK (metodo IN ('PIX','CARTAO')),
    CONSTRAINT ck_pagamento_status CHECK (status IN ('PENDENTE','APROVADO','RECUSADO','CANCELADO','ESTORNADO','PARCIALMENTE_ESTORNADO')),
    CONSTRAINT ck_pagamento_valores CHECK (valor >= 0 AND taxa >= 0 AND juros >= 0)
);

CREATE TABLE parcelas (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pagamento_id BIGINT NOT NULL REFERENCES pagamentos(id) ON DELETE RESTRICT,
    numero INTEGER NOT NULL,
    valor NUMERIC(12,2) NOT NULL,
    vencimento DATE,
    status VARCHAR(25) NOT NULL DEFAULT 'PENDENTE',
    pago_em TIMESTAMPTZ,
    CONSTRAINT uq_parcela_numero UNIQUE (pagamento_id, numero),
    CONSTRAINT ck_parcela_numero CHECK (numero BETWEEN 1 AND 3),
    CONSTRAINT ck_parcela_valor CHECK (valor >= 0),
    CONSTRAINT ck_parcela_status CHECK (status IN ('PENDENTE','PAGA','CANCELADA','ESTORNADA'))
);

CREATE TABLE devolucoes (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pedido_id BIGINT NOT NULL REFERENCES pedidos(id) ON DELETE RESTRICT,
    usuario_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE RESTRICT,
    status VARCHAR(25) NOT NULL DEFAULT 'SOLICITADA',
    motivo TEXT NOT NULL,
    data_solicitacao TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_recebimento TIMESTAMPTZ,
    custo_retorno NUMERIC(12,2) NOT NULL DEFAULT 0,
    CONSTRAINT ck_devolucao_status CHECK (status IN ('SOLICITADA','EM_ANALISE','APROVADA','RECUSADA','CONCLUIDA')),
    CONSTRAINT ck_devolucao_custo CHECK (custo_retorno >= 0)
);

CREATE TABLE itens_devolucao (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    devolucao_id BIGINT NOT NULL REFERENCES devolucoes(id) ON DELETE RESTRICT,
    produto_id BIGINT NOT NULL REFERENCES produtos(id) ON DELETE RESTRICT,
    item_pedido_id BIGINT NOT NULL REFERENCES itens_pedido(id) ON DELETE RESTRICT,
    quantidade INTEGER NOT NULL,
    produto_lacrado BOOLEAN NOT NULL DEFAULT FALSE,
    motivo VARCHAR(255),
    CONSTRAINT ck_item_devolucao_quantidade CHECK (quantidade > 0),
    CONSTRAINT uq_item_devolucao UNIQUE (devolucao_id, item_pedido_id)
);

CREATE TABLE estornos (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    devolucao_id BIGINT NOT NULL UNIQUE REFERENCES devolucoes(id) ON DELETE RESTRICT,
    pagamento_id BIGINT NOT NULL REFERENCES pagamentos(id) ON DELETE RESTRICT,
    valor NUMERIC(12,2) NOT NULL,
    motivo VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'SOLICITADO',
    criado_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processado_at TIMESTAMPTZ,
    CONSTRAINT ck_estorno_valor CHECK (valor > 0),
    CONSTRAINT ck_estorno_status CHECK (status IN ('SOLICITADO','PROCESSANDO','CONCLUIDO','RECUSADO'))
);

CREATE TABLE webhooks_pagamento (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pagamento_id BIGINT NOT NULL REFERENCES pagamentos(id) ON DELETE RESTRICT,
    identificador_gateway VARCHAR(150) NOT NULL UNIQUE,
    tipo_evento VARCHAR(80) NOT NULL,
    payload JSONB NOT NULL,
    processado BOOLEAN NOT NULL DEFAULT FALSE,
    recebido_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processado_at TIMESTAMPTZ
);

ALTER TABLE movimentacoes_estoque ADD CONSTRAINT fk_movimentacao_item_devolucao FOREIGN KEY (item_devolucao_id) REFERENCES itens_devolucao(id) ON DELETE RESTRICT;

-- 7. FINANCEIRO, ADMINISTRACAO E REPOSICAO
CREATE TABLE configuracoes_financeiras (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    taxa_pix NUMERIC(7,4) NOT NULL DEFAULT 0,
    taxa_cartao NUMERIC(7,4) NOT NULL DEFAULT 0,
    atualizado_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_config_financeira_taxas CHECK (taxa_pix >= 0 AND taxa_cartao >= 0)
);

CREATE TABLE registros_financeiros (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pedido_id BIGINT NOT NULL REFERENCES pedidos(id) ON DELETE RESTRICT,
    valor_venda NUMERIC(12,2) NOT NULL,
    custo_fabrica NUMERIC(12,2) NOT NULL,
    taxas_financeiras NUMERIC(12,2) NOT NULL DEFAULT 0,
    lucro NUMERIC(12,2) NOT NULL,
    criado_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_registro_financeiro_valores CHECK (valor_venda >= 0 AND custo_fabrica >= 0 AND taxas_financeiras >= 0)
);

CREATE TABLE notificacoes_admin (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tipo VARCHAR(50) NOT NULL,
    pedido_id BIGINT REFERENCES pedidos(id) ON DELETE RESTRICT,
    usuario_id BIGINT REFERENCES usuarios(id) ON DELETE RESTRICT,
    visualizada BOOLEAN NOT NULL DEFAULT FALSE,
    criada_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_notificacao_destinatario CHECK (usuario_id IS NOT NULL OR tipo IS NOT NULL)
);

CREATE TABLE analises_reposicao (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    produto_id BIGINT NOT NULL REFERENCES produtos(id) ON DELETE RESTRICT,
    usuario_id BIGINT REFERENCES usuarios(id) ON DELETE RESTRICT,
    pedido_id BIGINT REFERENCES pedidos(id) ON DELETE RESTRICT,
    data_compra DATE,
    tempo_medio_uso_dias INTEGER,
    quantidade_sugerida NUMERIC(12,2) NOT NULL DEFAULT 0,
    status VARCHAR(25) NOT NULL DEFAULT 'PENDENTE',
    criada_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizada_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_analise_reposicao_valores CHECK (quantidade_sugerida >= 0 AND (tempo_medio_uso_dias IS NULL OR tempo_medio_uso_dias > 0))
);

-- 8. INDICES DE APOIO
CREATE INDEX idx_enderecos_usuario ON enderecos(usuario_id);
CREATE INDEX idx_telefones_usuario ON telefones(usuario_id);
CREATE INDEX idx_termos_usuario_data ON termos_aceites(usuario_id, data_hora_aceite DESC);
CREATE INDEX idx_recuperacoes_usuario_expiracao ON recuperacoes_senha(usuario_id, expiracao DESC);
CREATE INDEX idx_produtos_categoria_ativo ON produtos(categoria_id, ativo) WHERE deleted_at IS NULL;
CREATE INDEX idx_produtos_nome ON produtos USING gin (to_tsvector('portuguese', coalesce(nome,'') || ' ' || coalesce(descricao,'')));
CREATE INDEX idx_kit_itens_componente ON kit_itens(produto_componente_id);
CREATE INDEX idx_itens_carrinho_carrinho ON itens_carrinho(carrinho_id);
CREATE INDEX idx_pedidos_usuario_data ON pedidos(usuario_id, data_pedido DESC);
CREATE INDEX idx_pedidos_status ON pedidos(status, status_pagamento);
CREATE INDEX idx_itens_pedido_pedido ON itens_pedido(pedido_id);
CREATE INDEX idx_reservas_estoque_status ON reservas_estoque(status, expira_at);
CREATE INDEX idx_movimentacoes_estoque_data ON movimentacoes_estoque(estoque_id, data_movimentacao DESC);
CREATE INDEX idx_alertas_validade_data ON alertas_validade(data_prevista, status);
CREATE INDEX idx_pagamentos_status ON pagamentos(status);
CREATE INDEX idx_webhooks_pendentes ON webhooks_pagamento(recebido_at) WHERE processado = FALSE;
CREATE INDEX idx_notificacoes_admin_pedido ON notificacoes_admin(pedido_id, visualizada);
CREATE INDEX idx_analises_reposicao_produto ON analises_reposicao(produto_id, criada_at DESC);
CREATE INDEX idx_devolucoes_pedido ON devolucoes(pedido_id);
CREATE INDEX idx_itens_devolucao_devolucao ON itens_devolucao(devolucao_id);
CREATE INDEX idx_estornos_pagamento ON estornos(pagamento_id);

COMMIT;

-- REGRAS E DECISOES DE MODELAGEM
-- 1. A idade minima de cadastro deve ser validada pela aplicacao; nao usar CHECK com CURRENT_DATE.
-- 2. Produto representa produto simples ou kit. O kit e composto em kit_itens; tipo_kit distingue MANUAL/PRONTO.
-- 3. Estoque fisico deve ser controlado para modalidade PRONTA_ENTREGA; SOB_ENCOMENDA nao exige saldo fisico.
-- 4. Pagamento por PIX usa uma parcela; cartao admite no maximo 3 parcelas. Validar esta regra na aplicacao/trigger.
-- 5. Historico de preco nao e armazenado. preco_base e obrigatorio; preco_promocional/data_fim_promocao sao opcionais.
-- 6. Snapshot de endereco do pedido preserva o endereco de entrega no momento da compra.
-- 7. Campos complementares inferidos foram adicionados para viabilizar os requisitos nao detalhados no diagrama.
-- 8. Revisar cardinalidades marcadas 1:1/N.
-- 9. Testar esta migration em banco PostgreSQL limpo antes de integrar ao Flyway.
