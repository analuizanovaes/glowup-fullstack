-- =====================================================================================================
-- GlowUp: limpa os dados gerados por testes de compra (pedidos, pagamentos, devoluções, financeiro...).
--
-- QUANDO USAR: uma vez, no banco "glowup" LOCAL que já tem compras de teste. Num banco novo/vazio não há o
-- que limpar (as migrations só criam estrutura, categorias, os 50 produtos e os cupons de teste).
--
-- O QUE FAZ
--   * Devolve ao estoque as unidades que as compras de teste tinham baixado (saídas menos reintegrações).
--   * Apaga pedidos e tudo que depende deles: itens, endereço do pedido, histórico de status, pagamentos,
--     parcelas, webhooks, devoluções, estornos, registros financeiros, notificações e análises de reposição
--     ligadas a pedidos, e as movimentações de estoque geradas por pedidos/devoluções.
--   * Apaga carrinhos e itens de carrinho.
--   * Zera o contador de usos dos cupons e reinicia a numeração (id) das tabelas esvaziadas.
--   * Apaga as contas de CLIENTE criadas nos testes (com endereços, telefones e aceites de termos).
--   * Reativa os produtos que foram inativados nos testes (volta aos 50 do catálogo).
--
-- O QUE NÃO TOCA: a conta da administradora, produtos ativos, kits, categorias, cupons, entradas e ajustes de
--   estoque feitos por você, configurações e o histórico do Flyway (a API aplica as migrations ao subir).
--
-- COMO RODAR (pare a API antes, para não haver conexões escrevendo):
--   psql -U postgres -d glowup -f api/scripts/limpar-dados-de-teste.sql
-- (ou cole o conteúdo no Query Tool do pgAdmin conectado ao banco "glowup").
-- Tudo roda numa única transação: se algo falhar, nada é alterado.
-- =====================================================================================================

BEGIN;

-- 1) Devolve ao estoque o que as compras de teste baixaram (SAIDA soma, REINTEGRACAO subtrai).
UPDATE estoques e
SET quantidade = e.quantidade + v.liquido,
    atualizado_at = CURRENT_TIMESTAMP
FROM (
    SELECT estoque_id,
           SUM(CASE tipo WHEN 'SAIDA' THEN quantidade WHEN 'REINTEGRACAO' THEN -quantidade ELSE 0 END) AS liquido
    FROM movimentacoes_estoque
    WHERE pedido_id IS NOT NULL OR item_devolucao_id IS NOT NULL
    GROUP BY estoque_id
) v
WHERE e.id = v.estoque_id
  AND v.liquido > 0;

-- 2) Movimentações geradas por pedidos/devoluções (as entradas e ajustes manuais ficam).
DELETE FROM movimentacoes_estoque WHERE pedido_id IS NOT NULL OR item_devolucao_id IS NOT NULL;

-- 3) Financeiro, pagamentos e devoluções (ordem respeita as chaves estrangeiras).
DELETE FROM webhooks_pagamento;
DELETE FROM estornos;
DELETE FROM parcelas;
DELETE FROM itens_devolucao;
DELETE FROM devolucoes;
DELETE FROM registros_financeiros;
DELETE FROM pagamentos;

-- 4) Notificações e análises que nasceram de pedidos.
DELETE FROM notificacoes_admin WHERE pedido_id IS NOT NULL;
DELETE FROM analises_reposicao WHERE pedido_id IS NOT NULL;

-- 5) Pedidos e seus dados.
DELETE FROM historico_status_pedido;
DELETE FROM pedido_enderecos;
DELETE FROM itens_pedido;
DELETE FROM pedidos;

-- 6) Carrinhos.
DELETE FROM itens_carrinho;
DELETE FROM carrinhos;

-- 7) Cupons voltam a ter todos os usos disponíveis.
UPDATE cupons SET usos_realizados = 0;

-- 8) Reinicia a numeração das tabelas esvaziadas (pedido #1, #2...).
DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['webhooks_pagamento', 'estornos', 'parcelas', 'itens_devolucao', 'devolucoes',
                             'registros_financeiros', 'pagamentos', 'historico_status_pedido', 'pedido_enderecos',
                             'itens_pedido', 'pedidos', 'itens_carrinho', 'carrinhos']
    LOOP
        EXECUTE format('SELECT setval(pg_get_serial_sequence(%L, ''id''), 1, false)', 'public.' || t);
    END LOOP;
END
$$;

-- 9) Contas de CLIENTE criadas nos testes (a administradora é preservada). Quem testou precisará se cadastrar de novo.
DELETE FROM notificacoes_admin WHERE usuario_id IN (SELECT id FROM usuarios WHERE role <> 'ADMIN');
DELETE FROM analises_reposicao WHERE usuario_id IN (SELECT id FROM usuarios WHERE role <> 'ADMIN');
DELETE FROM refresh_tokens     WHERE usuario_id IN (SELECT id FROM usuarios WHERE role <> 'ADMIN');
DELETE FROM recuperacoes_senha WHERE usuario_id IN (SELECT id FROM usuarios WHERE role <> 'ADMIN');
DELETE FROM termos_aceites     WHERE usuario_id IN (SELECT id FROM usuarios WHERE role <> 'ADMIN');
DELETE FROM telefones          WHERE usuario_id IN (SELECT id FROM usuarios WHERE role <> 'ADMIN');
DELETE FROM enderecos          WHERE usuario_id IN (SELECT id FROM usuarios WHERE role <> 'ADMIN');
DELETE FROM usuarios           WHERE role <> 'ADMIN';

-- 10) Produtos inativados nos testes voltam ao catálogo (o catálogo oficial tem 50 produtos).
--     Quem prefere escolher um a um pode usar a tela "Produtos inativos" no painel; nesse caso, apague este comando.
UPDATE produtos SET ativo = TRUE, deleted_at = NULL, updated_at = CURRENT_TIMESTAMP WHERE ativo = FALSE;

-- Conferência: as 5 primeiras colunas devem mostrar 0; produtos_ativos mostra o catálogo (50 no catálogo original).
SELECT (SELECT count(*) FROM pedidos)        AS pedidos,
       (SELECT count(*) FROM pagamentos)     AS pagamentos,
       (SELECT count(*) FROM devolucoes)     AS devolucoes,
       (SELECT count(*) FROM carrinhos)      AS carrinhos,
       (SELECT count(*) FROM registros_financeiros) AS registros_financeiros,
       (SELECT count(*) FROM produtos WHERE ativo) AS produtos_ativos;

COMMIT;
