-- GlowUp | V23: imagens (ilustrações em SVG) dos 50 produtos do catálogo inicial.
-- Os arquivos ficam em frontend/assets/img/produtos/ e são servidos pelo próprio front-end (caminho relativo).
-- Só preenche produtos que ainda NÃO têm imagem (imagem_url nula ou vazia): nunca sobrescreve foto enviada pela Domingas.
-- Para trocar por uma foto real, basta editar o produto no painel e enviar a nova imagem.

UPDATE produtos SET imagem_url = 'assets/img/produtos/01-creme-acetinado-hidratante-desodorante-corporal.svg'
WHERE nome = 'Creme Acetinado Hidratante Desodorante Corporal Lily 250g' AND (imagem_url IS NULL OR imagem_url = '');

UPDATE produtos SET imagem_url = 'assets/img/produtos/02-body-splash-desodorante-colonia-lily-200ml.svg'
WHERE nome = 'Body Splash Desodorante Colônia Lily 200ml' AND (imagem_url IS NULL OR imagem_url = '');

UPDATE produtos SET imagem_url = 'assets/img/produtos/03-desodorante-antitranspirante-aerossol-lily-75g-1.svg'
WHERE nome = 'Desodorante Antitranspirante Aerossol Lily 75g/125ml' AND (imagem_url IS NULL OR imagem_url = '');

UPDATE produtos SET imagem_url = 'assets/img/produtos/04-malbec-desodorante-colonia-100ml.svg'
WHERE nome = 'Malbec Desodorante Colônia 100ml' AND (imagem_url IS NULL OR imagem_url = '');

UPDATE produtos SET imagem_url = 'assets/img/produtos/05-arbo-desodorante-colonia-100ml.svg'
WHERE nome = 'Arbo Desodorante Colônia 100ml' AND (imagem_url IS NULL OR imagem_url = '');

UPDATE produtos SET imagem_url = 'assets/img/produtos/06-accordes-desodorante-colonia-80ml.svg'
WHERE nome = 'Accordes Desodorante Colônia 80ml' AND (imagem_url IS NULL OR imagem_url = '');

UPDATE produtos SET imagem_url = 'assets/img/produtos/07-floratta-rose-desodorante-colonia-75ml.svg'
WHERE nome = 'Floratta Rose Desodorante Colônia 75ml' AND (imagem_url IS NULL OR imagem_url = '');

UPDATE produtos SET imagem_url = 'assets/img/produtos/08-her-code-eau-de-parfum-50ml.svg'
WHERE nome = 'Her Code Eau de Parfum 50ml' AND (imagem_url IS NULL OR imagem_url = '');

UPDATE produtos SET imagem_url = 'assets/img/produtos/09-floratta-red-desodorante-colonia-75ml.svg'
WHERE nome = 'Floratta Red Desodorante Colônia 75ml' AND (imagem_url IS NULL OR imagem_url = '');

UPDATE produtos SET imagem_url = 'assets/img/produtos/10-egeo-original-desodorante-colonia-90ml.svg'
WHERE nome = 'Egeo Original Desodorante Colônia 90ml' AND (imagem_url IS NULL OR imagem_url = '');

UPDATE produtos SET imagem_url = 'assets/img/produtos/11-egeo-dolce-desodorante-colonia-90ml.svg'
WHERE nome = 'Egeo Dolce Desodorante Colônia 90ml' AND (imagem_url IS NULL OR imagem_url = '');

UPDATE produtos SET imagem_url = 'assets/img/produtos/12-coffee-woman-desodorante-colonia-100ml.svg'
WHERE nome = 'Coffee Woman Desodorante Colônia 100ml' AND (imagem_url IS NULL OR imagem_url = '');

UPDATE produtos SET imagem_url = 'assets/img/produtos/13-body-splash-desodorante-colonia-l-eau-de-lily-20.svg'
WHERE nome = 'Body Splash Desodorante Colônia L''eau de Lily 200ml' AND (imagem_url IS NULL OR imagem_url = '');

UPDATE produtos SET imagem_url = 'assets/img/produtos/14-liz-desodorante-colonia-100ml.svg'
WHERE nome = 'Liz Desodorante Colônia 100ml' AND (imagem_url IS NULL OR imagem_url = '');

UPDATE produtos SET imagem_url = 'assets/img/produtos/15-creme-desodorante-hidratante-corporal-liz-250g.svg'
WHERE nome = 'Creme Desodorante Hidratante Corporal Liz 250g' AND (imagem_url IS NULL OR imagem_url = '');

UPDATE produtos SET imagem_url = 'assets/img/produtos/16-creme-acetinado-hidratante-desodorante-corporal.svg'
WHERE nome = 'Creme Acetinado Hidratante Desodorante Corporal Elysée 250g' AND (imagem_url IS NULL OR imagem_url = '');

UPDATE produtos SET imagem_url = 'assets/img/produtos/17-linda-desodorante-colonia-100ml.svg'
WHERE nome = 'Linda Desodorante Colônia 100ml' AND (imagem_url IS NULL OR imagem_url = '');

UPDATE produtos SET imagem_url = 'assets/img/produtos/18-oleo-desodorante-corporal-nativa-spa-ameixa-200m.svg'
WHERE nome = 'Óleo Desodorante Corporal Nativa SPA Ameixa 200ml' AND (imagem_url IS NULL OR imagem_url = '');

UPDATE produtos SET imagem_url = 'assets/img/produtos/19-zaad-eau-de-parfum-95ml.svg'
WHERE nome = 'Zaad Eau de Parfum 95ml' AND (imagem_url IS NULL OR imagem_url = '');

UPDATE produtos SET imagem_url = 'assets/img/produtos/20-glamour-desodorante-colonia-75ml.svg'
WHERE nome = 'Glamour Desodorante Colônia 75ml' AND (imagem_url IS NULL OR imagem_url = '');

UPDATE produtos SET imagem_url = 'assets/img/produtos/21-quasar-classic-desodorante-colonia-100ml.svg'
WHERE nome = 'Quasar Classic Desodorante Colônia 100ml' AND (imagem_url IS NULL OR imagem_url = '');

UPDATE produtos SET imagem_url = 'assets/img/produtos/22-boticollection-portinari-desodorante-colonia-100.svg'
WHERE nome = 'Boticollection Portinari Desodorante Colônia 100ml' AND (imagem_url IS NULL OR imagem_url = '');

UPDATE produtos SET imagem_url = 'assets/img/produtos/23-desodorante-colonia-kaiak-masculino-100ml.svg'
WHERE nome = 'Desodorante Colônia Kaiak Masculino 100ml' AND (imagem_url IS NULL OR imagem_url = '');

UPDATE produtos SET imagem_url = 'assets/img/produtos/24-desodorante-body-spray-kaiak-masculino-100ml.svg'
WHERE nome = 'Desodorante Body Spray Kaiak Masculino 100ml' AND (imagem_url IS NULL OR imagem_url = '');

UPDATE produtos SET imagem_url = 'assets/img/produtos/25-desodorante-colonia-kaiak-feminino-100ml.svg'
WHERE nome = 'Desodorante Colônia Kaiak Feminino 100ml' AND (imagem_url IS NULL OR imagem_url = '');

UPDATE produtos SET imagem_url = 'assets/img/produtos/26-desodorante-body-spray-kaiak-feminino-100ml.svg'
WHERE nome = 'Desodorante Body Spray Kaiak Feminino 100ml' AND (imagem_url IS NULL OR imagem_url = '');

UPDATE produtos SET imagem_url = 'assets/img/produtos/27-desodorante-colonia-luna-feminino-75ml.svg'
WHERE nome = 'Desodorante Colônia Luna Feminino 75ml' AND (imagem_url IS NULL OR imagem_url = '');

UPDATE produtos SET imagem_url = 'assets/img/produtos/28-caixa-de-sabonetes-em-barra-puro-vegetal-tododia.svg'
WHERE nome = 'Caixa de Sabonetes em Barra Puro Vegetal Tododia (5 un. de 90g)' AND (imagem_url IS NULL OR imagem_url = '');

UPDATE produtos SET imagem_url = 'assets/img/produtos/29-caixa-de-sabonetes-em-barra-tododia-macadamia-5.svg'
WHERE nome = 'Caixa de Sabonetes em Barra Tododia Macadâmia (5 un. de 90g)' AND (imagem_url IS NULL OR imagem_url = '');

UPDATE produtos SET imagem_url = 'assets/img/produtos/30-agua-de-colonia-mamae-e-bebe-100ml.svg'
WHERE nome = 'Água de Colônia Mamãe e Bebê 100ml' AND (imagem_url IS NULL OR imagem_url = '');

UPDATE produtos SET imagem_url = 'assets/img/produtos/31-sabonete-em-barra-mamae-e-bebe-caixa-c-5-un.svg'
WHERE nome = 'Sabonete em Barra Mamãe e Bebê (Caixa c/ 5 un.)' AND (imagem_url IS NULL OR imagem_url = '');

UPDATE produtos SET imagem_url = 'assets/img/produtos/32-shampoo-suave-mamae-e-bebe-200ml.svg'
WHERE nome = 'Shampoo Suave Mamãe e Bebê 200ml' AND (imagem_url IS NULL OR imagem_url = '');

UPDATE produtos SET imagem_url = 'assets/img/produtos/33-creme-para-pentear-mamae-e-bebe-cachos-200ml.svg'
WHERE nome = 'Creme para Pentear Mamãe e Bebê Cachos 200ml' AND (imagem_url IS NULL OR imagem_url = '');

UPDATE produtos SET imagem_url = 'assets/img/produtos/34-polpa-hidratante-para-maos-ekos-castanha-75g.svg'
WHERE nome = 'Polpa Hidratante para Mãos Ekos Castanha 75g' AND (imagem_url IS NULL OR imagem_url = '');

UPDATE produtos SET imagem_url = 'assets/img/produtos/35-oleo-desodorante-corporal-seve-amendoas-doces-20.svg'
WHERE nome = 'Óleo Desodorante Corporal Sève Amêndoas Doces 200ml' AND (imagem_url IS NULL OR imagem_url = '');

UPDATE produtos SET imagem_url = 'assets/img/produtos/36-deo-parfum-essencial-exclusivo-masculino-100ml.svg'
WHERE nome = 'Deo Parfum Essencial Exclusivo Masculino 100ml' AND (imagem_url IS NULL OR imagem_url = '');

UPDATE produtos SET imagem_url = 'assets/img/produtos/37-desodorante-antitranspirante-roll-on-tododia-sem.svg'
WHERE nome = 'Desodorante Antitranspirante Roll-On Tododia Sem Perfume 70ml' AND (imagem_url IS NULL OR imagem_url = '');

UPDATE produtos SET imagem_url = 'assets/img/produtos/38-shampoo-siage-acelera-o-crescimento-250ml.svg'
WHERE nome = 'Shampoo Siàge Acelera o Crescimento 250ml' AND (imagem_url IS NULL OR imagem_url = '');

UPDATE produtos SET imagem_url = 'assets/img/produtos/39-condicionador-siage-acelera-o-crescimento-200ml.svg'
WHERE nome = 'Condicionador Siàge Acelera o Crescimento 200ml' AND (imagem_url IS NULL OR imagem_url = '');

UPDATE produtos SET imagem_url = 'assets/img/produtos/40-leave-in-capilar-siage-nutri-oleos-poderosos-100.svg'
WHERE nome = 'Leave-in Capilar Siàge Nutri Óleos Poderosos 100ml' AND (imagem_url IS NULL OR imagem_url = '');

UPDATE produtos SET imagem_url = 'assets/img/produtos/41-desodorante-body-spray-la-victorie-intense-100ml.svg'
WHERE nome = 'Desodorante Body Spray La Victorie Intense 100ml' AND (imagem_url IS NULL OR imagem_url = '');

UPDATE produtos SET imagem_url = 'assets/img/produtos/42-body-splash-desodorante-colonia-eudora-200ml.svg'
WHERE nome = 'Body Splash Desodorante Colônia Eudora 200ml' AND (imagem_url IS NULL OR imagem_url = '');

UPDATE produtos SET imagem_url = 'assets/img/produtos/43-body-splash-desodorante-colonia-instance-ameixa.svg'
WHERE nome = 'Body Splash Desodorante Colônia Instance Ameixa e Praliné 200ml' AND (imagem_url IS NULL OR imagem_url = '');

UPDATE produtos SET imagem_url = 'assets/img/produtos/44-locao-hidratante-desodorante-corporal-instance-a.svg'
WHERE nome = 'Loção Hidratante Desodorante Corporal Instance Ameixa e Praliné 400ml' AND (imagem_url IS NULL OR imagem_url = '');

UPDATE produtos SET imagem_url = 'assets/img/produtos/45-protetor-solar-avon-care-sun-kids-fps-50-120g.svg'
WHERE nome = 'Protetor Solar Avon Care Sun+ Kids FPS 50 120g' AND (imagem_url IS NULL OR imagem_url = '');

UPDATE produtos SET imagem_url = 'assets/img/produtos/46-creme-redutor-de-cuticulas-mira-cuticle-complexo.svg'
WHERE nome = 'Creme Redutor de Cutículas Mira-Cuticle Complexo com AHA 20g' AND (imagem_url IS NULL OR imagem_url = '');

UPDATE produtos SET imagem_url = 'assets/img/produtos/47-esmalte-color-trend-efeito-crystal-rosa-crystal.svg'
WHERE nome = 'Esmalte Color Trend Efeito Crystal - Rosa Crystal' AND (imagem_url IS NULL OR imagem_url = '');

UPDATE produtos SET imagem_url = 'assets/img/produtos/48-esmalte-color-trend-efeito-crystal-preto-crystal.svg'
WHERE nome = 'Esmalte Color Trend Efeito Crystal - Preto Crystal' AND (imagem_url IS NULL OR imagem_url = '');

UPDATE produtos SET imagem_url = 'assets/img/produtos/49-esmalte-color-trend-efeito-crystal-vermelho-crys.svg'
WHERE nome = 'Esmalte Color Trend Efeito Crystal - Vermelho Crystal' AND (imagem_url IS NULL OR imagem_url = '');

UPDATE produtos SET imagem_url = 'assets/img/produtos/50-esmalte-color-trend-efeito-crystal-prata-crystal.svg'
WHERE nome = 'Esmalte Color Trend Efeito Crystal - Prata Crystal' AND (imagem_url IS NULL OR imagem_url = '');
