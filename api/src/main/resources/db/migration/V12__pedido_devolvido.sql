ALTER TABLE pedidos DROP CONSTRAINT IF EXISTS ck_pedido_status;
ALTER TABLE pedidos ADD CONSTRAINT ck_pedido_status
    CHECK (status IN ('AGUARDANDO_PAGAMENTO', 'PAGO', 'EM_SEPARACAO', 'ENVIADO', 'ENTREGUE', 'CANCELADO', 'DEVOLVIDO'));

ALTER TABLE historico_status_pedido DROP CONSTRAINT IF EXISTS ck_historico_status_anterior;
ALTER TABLE historico_status_pedido DROP CONSTRAINT IF EXISTS ck_historico_status_novo;
ALTER TABLE historico_status_pedido ADD CONSTRAINT ck_historico_status_anterior
    CHECK (status_anterior IS NULL OR status_anterior IN
        ('AGUARDANDO_PAGAMENTO', 'PAGO', 'EM_SEPARACAO', 'ENVIADO', 'ENTREGUE', 'CANCELADO', 'DEVOLVIDO'));
ALTER TABLE historico_status_pedido ADD CONSTRAINT ck_historico_status_novo
    CHECK (status_novo IN
        ('AGUARDANDO_PAGAMENTO', 'PAGO', 'EM_SEPARACAO', 'ENVIADO', 'ENTREGUE', 'CANCELADO', 'DEVOLVIDO'));
