-- A tabela reservas_estoque pertencia a um modelo de estoque abandonado: o estoque real é baixado de forma atômica
-- no checkout e devolvido no cancelamento (EstornoEstoqueService). Nenhuma entidade ou código a utiliza mais.
DROP TABLE IF EXISTS reservas_estoque;
