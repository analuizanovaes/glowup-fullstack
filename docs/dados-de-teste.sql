-- Dados de teste para a integração financeira (apenas ambiente local/sandbox).
-- Pré-requisito: o Flyway já rodou (a API subiu ao menos uma vez).
-- Cadastre antes, via POST /api/usuarios, as contas cliente.teste@glowup.com e admin.teste@glowup.com.
-- (Alternativa à promoção abaixo: defina ADMIN_EMAIL e ADMIN_SENHA_INICIAL e a API cria a admin sozinha.)

-- 1) Torna uma conta administradora
UPDATE usuarios SET role = 'ADMIN' WHERE email = 'admin.teste@glowup.com';

-- 2) Produto de teste de Pronta-Entrega com estoque 9
--    (o pedido abaixo simula a baixa de 1 unidade feita no checkout: de 10 sobram 9)
INSERT INTO produtos (categoria_id, nome, marca, custo_fabrica, preco_base, modalidade)
SELECT id, 'Produto Teste Pagamento', 'Teste', 40.00, 100.00, 'PRONTA_ENTREGA'
FROM categorias WHERE nome = 'Perfumaria';

INSERT INTO estoques (produto_id, quantidade)
SELECT id, 9 FROM produtos WHERE nome = 'Produto Teste Pagamento';

-- 3) Pedido aguardando pagamento da cliente de teste
INSERT INTO pedidos (usuario_id, subtotal, desconto, frete, total, status, cep_entrega, bairro_entrega)
SELECT id, 100.00, 0, 0, 100.00, 'AGUARDANDO_PAGAMENTO', '71592000', 'Itapoã'
FROM usuarios WHERE email = 'cliente.teste@glowup.com';

INSERT INTO itens_pedido (pedido_id, produto_id, quantidade, preco_praticado, custo_fabrica, subtotal)
SELECT (SELECT max(id) FROM pedidos),
       (SELECT max(id) FROM produtos WHERE nome = 'Produto Teste Pagamento'),
       1, 100.00, 40.00, 100.00;

-- 4) Descobrir o ID do pedido para a variável {{pedidoId}} do Postman
SELECT max(id) AS pedido_id FROM pedidos;

-- Verificação de estorno de estoque (após cancelar/rejeitar/expirar): deve voltar a 10
-- SELECT e.quantidade FROM estoques e JOIN produtos p ON p.id = e.produto_id WHERE p.nome = 'Produto Teste Pagamento';
