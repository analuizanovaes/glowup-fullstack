-- GlowUp | V3: carga inicial (seed) dos 50 produtos mais vendidos (RF02, secao 6.17 da documentacao).
-- Fonte: "Produtos mais Vendidos" (Domingas). Precos = "Preco Medio Estimado" do catalogo.
--
-- ATENCAO - premissas que a cliente precisa confirmar (a fonte nao traz estes dados):
--  1) custo_fabrica = 70% do preco de catalogo (margem de revenda estimada de 30%). Troque pelo custo real:
--     o Lucro Real (RN05) so e confiavel com o custo verdadeiro.
--  2) modalidade = SOB_ENCOMENDA para todos (nao ha informacao de estoque fisico). Itens que a Domingas
--     mantem em maos devem virar PRONTA_ENTREGA com quantidade informada em estoques.
--  3) tempo_medio_uso_dias e uma estimativa por tipo de produto, usada apenas pelo CRM de reposicao.
--
-- Esta migration e executada uma unica vez pelo Flyway; nao e idempotente por desenho.

INSERT INTO categorias (nome) VALUES
    ('Perfumaria'),
    ('Cuidados Diários'),
    ('Maquiagem');

WITH carga (posicao, nome, marca, categoria, preco_base, tempo_medio_uso_dias) AS (
    VALUES
    ( 1, 'Creme Acetinado Hidratante Desodorante Corporal Lily 250g', 'O Boticário', 'Cuidados Diários', 139.90, 60),
    ( 2, 'Body Splash Desodorante Colônia Lily 200ml', 'O Boticário', 'Perfumaria', 94.90, 75),
    ( 3, 'Desodorante Antitranspirante Aerossol Lily 75g/125ml', 'O Boticário', 'Cuidados Diários', 34.90, 45),
    ( 4, 'Malbec Desodorante Colônia 100ml', 'O Boticário', 'Perfumaria', 199.90, 120),
    ( 5, 'Arbo Desodorante Colônia 100ml', 'O Boticário', 'Perfumaria', 164.90, 120),
    ( 6, 'Accordes Desodorante Colônia 80ml', 'O Boticário', 'Perfumaria', 174.90, 90),
    ( 7, 'Floratta Rose Desodorante Colônia 75ml', 'O Boticário', 'Perfumaria', 149.90, 90),
    ( 8, 'Her Code Eau de Parfum 50ml', 'O Boticário', 'Perfumaria', 229.90, 60),
    ( 9, 'Floratta Red Desodorante Colônia 75ml', 'O Boticário', 'Perfumaria', 149.90, 90),
    (10, 'Egeo Original Desodorante Colônia 90ml', 'O Boticário', 'Perfumaria', 139.90, 120),
    (11, 'Egeo Dolce Desodorante Colônia 90ml', 'O Boticário', 'Perfumaria', 139.90, 120),
    (12, 'Coffee Woman Desodorante Colônia 100ml', 'O Boticário', 'Perfumaria', 194.90, 120),
    (13, 'Body Splash Desodorante Colônia L''eau de Lily 200ml', 'O Boticário', 'Perfumaria', 94.90, 75),
    (14, 'Liz Desodorante Colônia 100ml', 'O Boticário', 'Perfumaria', 154.90, 120),
    (15, 'Creme Desodorante Hidratante Corporal Liz 250g', 'O Boticário', 'Cuidados Diários', 74.90, 60),
    (16, 'Creme Acetinado Hidratante Desodorante Corporal Elysée 250g', 'O Boticário', 'Cuidados Diários', 149.90, 60),
    (17, 'Linda Desodorante Colônia 100ml', 'O Boticário', 'Perfumaria', 164.90, 120),
    (18, 'Óleo Desodorante Corporal Nativa SPA Ameixa 200ml', 'O Boticário', 'Cuidados Diários', 74.90, 60),
    (19, 'Zaad Eau de Parfum 95ml', 'O Boticário', 'Perfumaria', 299.90, 120),
    (20, 'Glamour Desodorante Colônia 75ml', 'O Boticário', 'Perfumaria', 164.90, 90),
    (21, 'Quasar Classic Desodorante Colônia 100ml', 'O Boticário', 'Perfumaria', 149.90, 120),
    (22, 'Boticollection Portinari Desodorante Colônia 100ml', 'O Boticário', 'Perfumaria', 154.90, 120),
    (23, 'Desodorante Colônia Kaiak Masculino 100ml', 'Natura', 'Perfumaria', 175.90, 120),
    (24, 'Desodorante Body Spray Kaiak Masculino 100ml', 'Natura', 'Perfumaria', 44.90, 60),
    (25, 'Desodorante Colônia Kaiak Feminino 100ml', 'Natura', 'Perfumaria', 175.90, 120),
    (26, 'Desodorante Body Spray Kaiak Feminino 100ml', 'Natura', 'Perfumaria', 44.90, 60),
    (27, 'Desodorante Colônia Luna Feminino 75ml', 'Natura', 'Perfumaria', 179.90, 90),
    (28, 'Caixa de Sabonetes em Barra Puro Vegetal Tododia (5 un. de 90g)', 'Natura', 'Cuidados Diários', 34.90, 75),
    (29, 'Caixa de Sabonetes em Barra Tododia Macadâmia (5 un. de 90g)', 'Natura', 'Cuidados Diários', 34.90, 75),
    (30, 'Água de Colônia Mamãe e Bebê 100ml', 'Natura', 'Perfumaria', 99.90, 120),
    (31, 'Sabonete em Barra Mamãe e Bebê (Caixa c/ 5 un.)', 'Natura', 'Cuidados Diários', 39.90, 75),
    (32, 'Shampoo Suave Mamãe e Bebê 200ml', 'Natura', 'Cuidados Diários', 37.90, 45),
    (33, 'Creme para Pentear Mamãe e Bebê Cachos 200ml', 'Natura', 'Cuidados Diários', 44.90, 45),
    (34, 'Polpa Hidratante para Mãos Ekos Castanha 75g', 'Natura', 'Cuidados Diários', 49.90, 60),
    (35, 'Óleo Desodorante Corporal Sève Amêndoas Doces 200ml', 'Natura', 'Cuidados Diários', 94.90, 60),
    (36, 'Deo Parfum Essencial Exclusivo Masculino 100ml', 'Natura', 'Perfumaria', 239.90, 120),
    (37, 'Desodorante Antitranspirante Roll-On Tododia Sem Perfume 70ml', 'Natura', 'Cuidados Diários', 25.90, 60),
    (38, 'Shampoo Siàge Acelera o Crescimento 250ml', 'Eudora', 'Cuidados Diários', 44.90, 45),
    (39, 'Condicionador Siàge Acelera o Crescimento 200ml', 'Eudora', 'Cuidados Diários', 47.90, 45),
    (40, 'Leave-in Capilar Siàge Nutri Óleos Poderosos 100ml', 'Eudora', 'Cuidados Diários', 54.90, 60),
    (41, 'Desodorante Body Spray La Victorie Intense 100ml', 'Eudora', 'Perfumaria', 49.90, 60),
    (42, 'Body Splash Desodorante Colônia Eudora 200ml', 'Eudora', 'Perfumaria', 74.90, 75),
    (43, 'Body Splash Desodorante Colônia Instance Ameixa e Praliné 200ml', 'Eudora', 'Perfumaria', 64.90, 75),
    (44, 'Loção Hidratante Desodorante Corporal Instance Ameixa e Praliné 400ml', 'Eudora', 'Cuidados Diários', 54.90, 75),
    (45, 'Protetor Solar Avon Care Sun+ Kids FPS 50 120g', 'Avon', 'Cuidados Diários', 49.90, 45),
    (46, 'Creme Redutor de Cutículas Mira-Cuticle Complexo com AHA 20g', 'Avon', 'Cuidados Diários', 15.90, 60),
    (47, 'Esmalte Color Trend Efeito Crystal - Rosa Crystal', 'Avon', 'Maquiagem', 21.90, 60),
    (48, 'Esmalte Color Trend Efeito Crystal - Preto Crystal', 'Avon', 'Maquiagem', 21.90, 60),
    (49, 'Esmalte Color Trend Efeito Crystal - Vermelho Crystal', 'Avon', 'Maquiagem', 21.90, 60),
    (50, 'Esmalte Color Trend Efeito Crystal - Prata Crystal', 'Avon', 'Maquiagem', 21.90, 60)
)
INSERT INTO produtos (categoria_id, nome, marca, custo_fabrica, preco_base, modalidade, tempo_medio_uso_dias)
SELECT c.id,
       carga.nome,
       carga.marca,
       ROUND(carga.preco_base * 0.70, 2),
       carga.preco_base,
       'SOB_ENCOMENDA',
       carga.tempo_medio_uso_dias
FROM carga
JOIN categorias c ON c.nome = carga.categoria
ORDER BY carga.posicao;
