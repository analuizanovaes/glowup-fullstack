DROP INDEX uq_devolucao_ativa_pedido;

CREATE UNIQUE INDEX uq_devolucao_ativa_pedido ON devolucoes(pedido_id)
    WHERE status IN ('SOLICITADA', 'APROVADA');
