-- Cupons de teste para apresentação/QA. Válidos até 2035, com usos generosos e mínimo de pedido realista.
INSERT INTO cupons (codigo, percentual_desconto, valor_desconto, valor_minimo, data_inicio, data_fim, limite_uso, usos_realizados, ativo)
VALUES
    ('GLOWUP10', 10.00, NULL, 0.00, '2024-01-01 00:00:00-03', '2035-12-31 23:59:59-03', 1000, 0, TRUE),
    ('WELCOME20', 20.00, NULL, 120.00, '2024-01-01 00:00:00-03', '2035-12-31 23:59:59-03', 500, 0, TRUE),
    ('DESCONTO15', 15.00, NULL, 80.00, '2024-01-01 00:00:00-03', '2035-12-31 23:59:59-03', 800, 0, TRUE),
    ('PRIMEIRACOMPRA', 25.00, NULL, 200.00, '2024-01-01 00:00:00-03', '2035-12-31 23:59:59-03', 300, 0, TRUE),
    ('FRETEGRATIS', NULL, 10.00, 150.00, '2024-01-01 00:00:00-03', '2035-12-31 23:59:59-03', 400, 0, TRUE)
ON CONFLICT (codigo) DO UPDATE SET
    percentual_desconto = EXCLUDED.percentual_desconto,
    valor_desconto = EXCLUDED.valor_desconto,
    valor_minimo = EXCLUDED.valor_minimo,
    data_inicio = EXCLUDED.data_inicio,
    data_fim = EXCLUDED.data_fim,
    limite_uso = EXCLUDED.limite_uso,
    usos_realizados = 0,
    ativo = TRUE;
