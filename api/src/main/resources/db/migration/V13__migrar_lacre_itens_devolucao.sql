UPDATE itens_devolucao i
SET produto_lacrado = COALESCE(d.produto_lacrado, FALSE)
FROM devolucoes d
WHERE d.id = i.devolucao_id;
