-- GlowUp | V2: correcoes sobre a V1 (a V1 nao e alterada para preservar o checksum do Flyway).
-- Cada bloco indica a regra de negocio / documento que motivou a mudanca.
-- Sem BEGIN/COMMIT: o Flyway ja executa cada migration em uma transacao.

-- 1. USUARIOS (RF01): e-mail unico sem diferenciar maiusculas/minusculas
CREATE UNIQUE INDEX uq_usuarios_email_lower ON usuarios (LOWER(email));

-- 2. RECUPERACOES_SENHA (RF01): limite de tentativas de OTP contra forca bruta
ALTER TABLE recuperacoes_senha ADD COLUMN tentativas INTEGER NOT NULL DEFAULT 0;
ALTER TABLE recuperacoes_senha ADD CONSTRAINT ck_recuperacao_tentativas CHECK (tentativas >= 0);

-- 3. PRODUTOS / KITS (DC02): a documentacao define TipoKit = FABRICA | MANUAL
ALTER TABLE produtos DROP CONSTRAINT ck_produtos_tipo_kit;
ALTER TABLE produtos ADD CONSTRAINT ck_produtos_tipo_kit
    CHECK (tipo_kit IS NULL OR tipo_kit IN ('FABRICA', 'MANUAL'));

-- 4. PEDIDOS (DC04 / RF08): StatusPedido = AGUARDANDO_PAGAMENTO, PAGO, EM_SEPARACAO, ENVIADO, ENTREGUE, CANCELADO
ALTER TABLE pedidos DROP CONSTRAINT ck_pedido_status;
UPDATE pedidos SET status = 'AGUARDANDO_PAGAMENTO' WHERE status = 'PROCESSANDO';
ALTER TABLE pedidos ADD CONSTRAINT ck_pedido_status
    CHECK (status IN ('AGUARDANDO_PAGAMENTO', 'PAGO', 'EM_SEPARACAO', 'ENVIADO', 'ENTREGUE', 'CANCELADO'));
ALTER TABLE pedidos ALTER COLUMN status SET DEFAULT 'AGUARDANDO_PAGAMENTO';

-- status_pagamento duplicava pagamentos.status e nunca seria atualizado (fonte unica da verdade: pagamentos)
DROP INDEX idx_pedidos_status;
ALTER TABLE pedidos DROP CONSTRAINT ck_pedido_status_pagamento;
ALTER TABLE pedidos DROP COLUMN status_pagamento;
CREATE INDEX idx_pedidos_status ON pedidos(status);

-- Snapshot minimo de entrega usado pelo checkout atual (RN03). Debito tecnico: migrar para pedido_enderecos.
ALTER TABLE pedidos ADD COLUMN cep_entrega VARCHAR(10);
ALTER TABLE pedidos ADD COLUMN bairro_entrega VARCHAR(100);

-- 5. PAGAMENTOS (RF04 / RN02 / RN04)
ALTER TABLE pagamentos DROP CONSTRAINT ck_pagamento_status;
UPDATE pagamentos SET status = 'REJEITADO' WHERE status = 'RECUSADO';
ALTER TABLE pagamentos ADD CONSTRAINT ck_pagamento_status
    CHECK (status IN ('PENDENTE', 'APROVADO', 'REJEITADO', 'CANCELADO', 'ESTORNADO'));

-- NULL = gateway nao informou o valor liquido; a taxa sera estimada (RN05)
ALTER TABLE pagamentos ALTER COLUMN taxa DROP NOT NULL;

ALTER TABLE pagamentos ADD COLUMN parcelas INTEGER NOT NULL DEFAULT 1;
ALTER TABLE pagamentos ADD COLUMN valor_liquido NUMERIC(12,2);
ALTER TABLE pagamentos ADD COLUMN status_detalhe_gateway VARCHAR(100);
ALTER TABLE pagamentos ADD COLUMN pix_copia_e_cola TEXT;
ALTER TABLE pagamentos ADD COLUMN pix_link_ticket VARCHAR(500);
ALTER TABLE pagamentos ADD COLUMN pix_expira_em TIMESTAMPTZ;
ALTER TABLE pagamentos ADD COLUMN data_pagamento TIMESTAMPTZ;

-- RN02: no maximo 3 parcelas, e parcelamento somente no cartao
ALTER TABLE pagamentos ADD CONSTRAINT ck_pagamento_parcelas
    CHECK (parcelas BETWEEN 1 AND 3 AND (metodo = 'CARTAO' OR parcelas = 1));
ALTER TABLE pagamentos ADD CONSTRAINT ck_pagamento_valor_liquido
    CHECK (valor_liquido IS NULL OR valor_liquido >= 0);
CREATE INDEX idx_pagamentos_pix_pendente ON pagamentos(pix_expira_em)
    WHERE metodo = 'PIX' AND status = 'PENDENTE';

-- 6. WEBHOOKS (RN04): o payload e armazenado como texto (dicionario de dados 10.3.8)
ALTER TABLE webhooks_pagamento ALTER COLUMN payload TYPE TEXT USING payload::text;

-- 7. REGISTROS_FINANCEIROS (RN05): "Dados Insuficientes" exige campos nulos
ALTER TABLE registros_financeiros ALTER COLUMN custo_fabrica DROP NOT NULL;
ALTER TABLE registros_financeiros ALTER COLUMN taxas_financeiras DROP NOT NULL;
ALTER TABLE registros_financeiros ALTER COLUMN taxas_financeiras DROP DEFAULT;
ALTER TABLE registros_financeiros ALTER COLUMN lucro DROP NOT NULL;
ALTER TABLE registros_financeiros ADD COLUMN juros NUMERIC(12,2);
ALTER TABLE registros_financeiros ADD COLUMN dados_suficientes BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE registros_financeiros ADD COLUMN estornado BOOLEAN NOT NULL DEFAULT FALSE;

-- Um unico registro por pedido garante a idempotencia da apuracao
ALTER TABLE registros_financeiros ADD CONSTRAINT uq_registro_financeiro_pedido UNIQUE (pedido_id);
ALTER TABLE registros_financeiros ADD CONSTRAINT ck_registro_financeiro_suficiencia
    CHECK (dados_suficientes = (custo_fabrica IS NOT NULL AND taxas_financeiras IS NOT NULL AND lucro IS NOT NULL));
-- Lucro Real = valorVenda - custoFabrica - taxasFinanceiras (RN05), garantido tambem no banco
ALTER TABLE registros_financeiros ADD CONSTRAINT ck_registro_financeiro_formula
    CHECK (NOT dados_suficientes OR lucro = valor_venda - custo_fabrica - taxas_financeiras);

-- 8. DEVOLUCOES (RN01 / RD03): StatusDevolucao = SOLICITADA, APROVADA, REJEITADA, RECEBIDA
ALTER TABLE devolucoes DROP CONSTRAINT ck_devolucao_status;
UPDATE devolucoes SET status = CASE status
    WHEN 'RECUSADA' THEN 'REJEITADA'
    WHEN 'CONCLUIDA' THEN 'RECEBIDA'
    WHEN 'EM_ANALISE' THEN 'SOLICITADA'
    ELSE status END;
ALTER TABLE devolucoes ADD CONSTRAINT ck_devolucao_status
    CHECK (status IN ('SOLICITADA', 'APROVADA', 'REJEITADA', 'RECEBIDA'));

ALTER TABLE devolucoes ADD COLUMN produto_lacrado BOOLEAN;
ALTER TABLE devolucoes ADD COLUMN observacao_analise VARCHAR(500);
ALTER TABLE devolucoes ADD COLUMN data_analise TIMESTAMPTZ;
ALTER TABLE devolucoes ADD COLUMN valor_estornado NUMERIC(12,2);
ALTER TABLE devolucoes ADD COLUMN id_estorno_gateway VARCHAR(50);

ALTER TABLE devolucoes ADD CONSTRAINT ck_devolucao_valor_estornado
    CHECK (valor_estornado IS NULL OR valor_estornado > 0);
-- RD03: produto com lacre violado jamais pode ser aprovado nem recebido para reintegrar o estoque
ALTER TABLE devolucoes ADD CONSTRAINT ck_devolucao_lacre
    CHECK (status NOT IN ('APROVADA', 'RECEBIDA') OR produto_lacrado IS TRUE);
-- No maximo uma devolucao ativa/concluida por pedido
CREATE UNIQUE INDEX uq_devolucao_ativa_pedido ON devolucoes(pedido_id)
    WHERE status IN ('SOLICITADA', 'APROVADA', 'RECEBIDA');

-- 9. HISTORICO DE STATUS DO PEDIDO (auditoria; base do prazo de 7 dias de devolucao)
CREATE TABLE historico_status_pedido (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pedido_id BIGINT NOT NULL REFERENCES pedidos(id) ON DELETE RESTRICT,
    status_anterior VARCHAR(30),
    status_novo VARCHAR(30) NOT NULL,
    origem VARCHAR(100) NOT NULL,
    ocorrido_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_historico_status_anterior CHECK (status_anterior IS NULL OR status_anterior IN
        ('AGUARDANDO_PAGAMENTO', 'PAGO', 'EM_SEPARACAO', 'ENVIADO', 'ENTREGUE', 'CANCELADO')),
    CONSTRAINT ck_historico_status_novo CHECK (status_novo IN
        ('AGUARDANDO_PAGAMENTO', 'PAGO', 'EM_SEPARACAO', 'ENVIADO', 'ENTREGUE', 'CANCELADO'))
);
CREATE INDEX idx_historico_status_pedido ON historico_status_pedido(pedido_id, ocorrido_em DESC);
