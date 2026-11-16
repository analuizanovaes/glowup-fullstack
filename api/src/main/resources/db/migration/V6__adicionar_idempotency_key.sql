-- Prevenção de cobrança dupla: chave de idempotência no checkout, única por cliente
ALTER TABLE pedidos ADD COLUMN idempotency_key VARCHAR(100);
ALTER TABLE pedidos ADD CONSTRAINT uk_pedido_cliente_idempotency UNIQUE (usuario_id, idempotency_key);