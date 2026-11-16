-- Diferencia lacre pendente de avaliação (NULL) de lacre avaliado como violado (FALSE).
ALTER TABLE itens_devolucao ALTER COLUMN produto_lacrado DROP NOT NULL;
UPDATE itens_devolucao i
SET produto_lacrado = NULL
FROM devolucoes d
WHERE i.devolucao_id = d.id AND d.status = 'SOLICITADA';
