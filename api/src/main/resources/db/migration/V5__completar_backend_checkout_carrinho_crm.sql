-- GlowUp | V5: completa o back-end conforme a documentacao (checkout seguro, carrinho, CRM e alertas).
-- Sem BEGIN/COMMIT: o Flyway ja executa cada migration em uma transacao. Nenhuma migration anterior foi alterada.

-- 1. PEDIDOS (RN03 / RF04): tipo de entrega fica registrado no pedido
ALTER TABLE pedidos ADD COLUMN tipo_entrega VARCHAR(20) NOT NULL DEFAULT 'DOMICILIO';
ALTER TABLE pedidos ADD CONSTRAINT ck_pedido_tipo_entrega CHECK (tipo_entrega IN ('DOMICILIO', 'RETIRADA'));

-- 2. NOTIFICACOES_ADMIN (dicionario 10.3.9): titulo e mensagem do alerta exibido no painel da administradora
ALTER TABLE notificacoes_admin ADD COLUMN titulo VARCHAR(150);
ALTER TABLE notificacoes_admin ADD COLUMN mensagem TEXT;

-- 3. ANALISES_REPOSICAO (DC07): data estimada para contato de reposicao
ALTER TABLE analises_reposicao ADD COLUMN data_estimada_reposicao DATE;

-- 4. Evita alerta/analise duplicados quando a rotina diaria roda mais de uma vez
CREATE UNIQUE INDEX uq_analise_reposicao_pedido_produto ON analises_reposicao(pedido_id, produto_id)
    WHERE pedido_id IS NOT NULL;
CREATE UNIQUE INDEX uq_alerta_validade_aberto_estoque ON alertas_validade(estoque_id)
    WHERE status = 'ABERTO';

-- 5. Tipo do UF como VARCHAR(2): o Hibernate valida VARCHAR e a coluna CHAR(2) (bpchar) seria rejeitada na subida
ALTER TABLE enderecos ALTER COLUMN uf TYPE VARCHAR(2);
ALTER TABLE pedido_enderecos ALTER COLUMN uf TYPE VARCHAR(2);
