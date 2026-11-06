/* GlowUp - camada de dados do front-end (localStorage).
   Simula o que o back-end fará (regras de negócio incluídas) para que todas as telas funcionem sozinhas.
   Quando a API estiver pronta, basta trocar o corpo destas funções por chamadas HTTP (fetch):
   as telas só conversam com GlowUp.store.* e nunca com o localStorage diretamente. */
(function (global) {
    'use strict';

    const GlowUp = (global.GlowUp = global.GlowUp || {});
    const U = GlowUp.utils;
    const C = GlowUp.config;
    const S = (GlowUp.store = {});

    /* ===================== armazenamento ===================== */
    function read(key, fallback) {
        try {
            const raw = localStorage.getItem(C.storagePrefix + key);
            return raw === null ? fallback : JSON.parse(raw);
        } catch (e) { return fallback; }
    }
    function write(key, value) {
        try { localStorage.setItem(C.storagePrefix + key, JSON.stringify(value)); return true; }
        catch (e) { return false; }
    }
    const nowISO = () => new Date().toISOString();
    const daysAgo = (n) => U.addDays(new Date(), -n).toISOString();
    const daysAhead = (n) => U.toISODate(U.addDays(new Date(), n));

    /* ===================== dados iniciais (seed) ===================== */
    const IMG = {
        a: 'https://images.unsplash.com/photo-1620916566398-39f1143ab7be?q=80&w=800&auto=format&fit=crop',
        b: 'https://images.unsplash.com/photo-1592945403244-b3fbafd7f539?q=80&w=800&auto=format&fit=crop',
        c: 'https://images.unsplash.com/photo-1522337360788-8b13dee7a37e?q=80&w=800&auto=format&fit=crop'
    };

    function seedProducts() {
        const P = 'PRONTA_ENTREGA', E = 'SOB_ENCOMENDA';
        const list = [
            [1, 'Kit Cuide-se Bem Rosé Hidratação', 'O Boticário', 'Kits e presentes', 'kit', P, 96.0, 139.9, null, null, 60, IMG.a, 'Kit presente com produtos de hidratação para o corpo, com toque suave e perfume delicado. Ideal para presentear ou se cuidar.'],
            [2, 'Essencial Exclusivo Feminino 100ml', 'Natura', 'Perfumaria feminina', 'varejo', P, 95.0, 159.9, 9, 300, 90, IMG.b, 'Perfume feminino com fragrância floral sofisticada e de longa duração, ideal para o dia a dia e ocasiões especiais.'],
            [3, 'Renew Sérum Facial Concentrado 30ml', 'Avon', 'Corpo e banho', 'varejo', P, 38.0, 69.9, 10, 280, 45, IMG.c, 'Sérum facial concentrado que ajuda a renovar e uniformizar a pele, com textura leve e rápida absorção.'],
            [4, 'Far Away Glamour Deo Parfum 50ml', 'Avon', 'Perfumaria feminina', 'varejo', P, 70.0, 119.9, 12, 22, 80, IMG.a, 'Deo parfum feminino de fragrância marcante e envolvente, perfeito para a noite e momentos de destaque.'],
            [5, 'Eudora Rouge Eau de Parfum 75ml', 'Eudora', 'Perfumaria feminina', 'varejo', E, 110.0, 189.9, null, null, 90, IMG.a, 'Eau de parfum feminino intenso e elegante, com fixação prolongada. Produto sob encomenda.'],
            [6, 'Tododia Creme Nutritivo Ameixa e Flor de Baunilha 400ml', 'Natura', 'Corpo e banho', 'varejo', P, 44.0, 79.9, 4, 260, 60, IMG.c, 'Creme nutritivo para o corpo com perfume de ameixa e flor de baunilha, deixa a pele macia e hidratada o dia todo.'],
            [7, 'Creme Acetinado Lily 250g', 'O Boticário', 'Corpo e banho', 'varejo', P, 82.0, 139.9, 5, 18, 90, IMG.a, 'Creme corporal acetinado que hidrata e perfuma a pele, deixando um toque aveludado e fragrância floral.'],
            [8, 'Malbec Desodorante Colônia 100ml', 'O Boticário', 'Perfumaria masculina', 'varejo', P, 120.0, 199.9, 8, 400, 90, IMG.b, 'Desodorante colônia masculino de fragrância amadeirada e marcante, para quem gosta de deixar presença.'],
            [9, 'Desodorante Colônia Kaiak Feminino 100ml', 'Natura', 'Perfumaria feminina', 'varejo', P, 105.0, 175.9, 7, 380, 80, IMG.c, 'Desodorante colônia feminino de fragrância fresca e leve, ideal para o dia a dia.'],
            [10, 'Malbec Gold Desodorante Colônia 100ml', 'O Boticário', 'Perfumaria masculina', 'varejo', P, 82.0, 139.9, 3, 350, 90, IMG.b, 'Desodorante colônia masculino com notas amadeiradas intensas e sofisticadas, de longa duração.'],
            [11, 'Far Away Beyond Deo Parfum 50ml', 'Avon', 'Perfumaria feminina', 'varejo', E, 62.0, 109.9, null, null, 80, IMG.a, 'Deo parfum feminino de fragrância sofisticada e envolvente. Produto sob encomenda.'],
            [12, 'Kit Presente Instance Baunilha', 'Eudora', 'Kits e presentes', 'kit', E, 55.0, 94.9, null, null, 60, IMG.c, 'Kit presente com fragrância de baunilha e cuidados para o corpo, pronto para presentear. Produto sob encomenda.'],
            [13, 'Ekos Castanha Creme Corporal 250g', 'Natura', 'Corpo e banho', 'varejo', P, 52.0, 84.9, 6, 330, 60, IMG.b, 'Creme corporal nutritivo com perfume de castanha, que hidrata profundamente e deixa a pele macia.']
        ];
        const adminOrder = [7, 8, 9, 4, 5, 6, 10, 13, 1, 2, 3, 11, 12];
        return list.map(r => ({
            id: r[0], nome: r[1], marca: r[2], categoria: r[3], tipoVenda: r[4], modalidade: r[5],
            custo: r[6], precoBase: r[7],
            precoPromocional: null, dataFimPromocao: null,
            estoque: r[8], reservado: 0, validade: r[9] === null ? null : daysAhead(r[9]),
            usoMedioDias: r[10], imagem: r[11], descricao: r[12], ativo: true,
            // KITS (DC02): tipoKit = 'FABRICA' (kit pronto, estoque próprio) ou 'MANUAL' (baixa os componentes).
            // O kit 1 é manual (1 Tododia + 1 Ekos); o kit 12 é pronto (sob encomenda).
            tipoKit: r[0] === 1 ? 'MANUAL' : (r[0] === 12 ? 'FABRICA' : null),
            itensKit: r[0] === 1 ? [{ produtoId: 6, qtd: 1 }, { produtoId: 13, qtd: 1 }] : null,
            atualizadoEm: new Date(Date.now() - adminOrder.indexOf(r[0]) * 60000).toISOString()
        }));
    }

    function seedUsers() {
        const pw = U.hash('Glow@1234');
        const mk = (id, nome, nasc, email, tel, role, enderecos) => ({
            id, nome, nascimento: nasc, email, telefones: [tel], senhaHash: pw, role, ativo: true,
            enderecos: enderecos || [], criadoEm: daysAgo(200),
            aceiteTermos: { dataHora: daysAgo(200), versao: C.termsVersion }
        });
        return [
            mk(1, 'Domingas Ferreira', '12/04/1980', 'domingas@glowup.com', '(61) 99999-0000', 'ADMIN'),
            mk(2, 'Mariana Alves', '15/03/1995', 'mariana@email.com', '(61) 99888-1122', 'CLIENT', [
                { id: 1, apelido: 'Casa', cep: '71593-100', rua: 'Quadra 202 Conjunto 8', numero: '15', bairro: 'Itapoã', complemento: '', cidade: 'Brasília', uf: 'DF', principal: true },
                { id: 2, apelido: 'Trabalho', cep: '71570-030', rua: 'Quadra 3 Conjunto 2', numero: '40', bairro: 'Paranoá', complemento: 'Sala 2', cidade: 'Brasília', uf: 'DF', principal: false }
            ]),
            mk(3, 'Joana Lima', '02/09/1990', 'joana@email.com', '(61) 99777-3344', 'CLIENT'),
            mk(4, 'Renata Costa', '21/11/1988', 'renata@email.com', '(61) 99666-5566', 'CLIENT'),
            mk(5, 'Ana Silva', '30/06/1998', 'ana@email.com', '(61) 99555-7788', 'CLIENT')
        ];
    }

    function seedCoupons() {
        return [
            { codigo: 'GLOW10', percentual: 10, valor: 0, minimo: 0, ativo: true, inicio: daysAgo(30).slice(0, 10), fim: daysAhead(365) },
            { codigo: 'BEMVINDA', percentual: 0, valor: 15, minimo: 80, ativo: true, inicio: daysAgo(30).slice(0, 10), fim: daysAhead(365) }
        ];
    }

    function seedOrders(users, products) {
        const orders = [];
        const prod = (id) => products.find(p => p.id === id);
        const home = (u) => ({ tipo: 'home', prazoDias: C.deliveryDays, endereco: Object.assign({}, (u.enderecos[0] || { cep: '71593-100', rua: 'Quadra 202 Conjunto 8', numero: '15', bairro: 'Itapoã', complemento: '', cidade: 'Brasília', uf: 'DF' })) });
        const pick = () => ({ tipo: 'pickup', prazoDias: C.deliveryDays, endereco: null });
        const defs = [
            [1048, 2, [[8, 1], [4, 1]], 'home', { metodo: 'PIX' }, 'Entregue', 8, 2],
            [1031, 2, [[2, 1]], 'pickup', { metodo: 'CARTAO', parcelas: 2 }, 'Enviado', 9, null, 3.14],
            [1022, 2, [[6, 2]], 'home', { metodo: 'PIX' }, 'Pago', 6, null],
            [1009, 2, [[10, 1]], 'home', { metodo: 'PIX' }, 'Entregue', 29, 22],
            [987, 2, [[7, 1]], 'pickup', { metodo: 'PIX' }, 'Entregue', 84, 84],
            [960, 2, [[9, 1]], 'home', { metodo: 'PIX' }, 'Entregue', 100, 93],
            [975, 3, [[8, 1]], 'pickup', { metodo: 'PIX' }, 'Entregue', 81, 81],
            [971, 4, [[9, 1]], 'pickup', { metodo: 'PIX' }, 'Entregue', 68, 68],
            [969, 5, [[4, 1]], 'pickup', { metodo: 'PIX' }, 'Entregue', 65, 65]
        ];
        defs.forEach(d => {
            const u = users.find(x => x.id === d[1]);
            const items = d[2].map(([pid, qtd]) => ({ produto: prod(pid), qtd }));
            const entrega = d[3] === 'home' ? home(u) : pick();
            const pagamento = Object.assign({ parcelas: 1 }, d[4]);
            const t = computeTotals(items.map(i => ({ precoPraticado: i.produto.precoBase, custoFabrica: i.produto.custo, qtd: i.qtd })), null, entrega.tipo, pagamento);
            if (d[8] != null) { t.juros = d[8]; t.total = U.round2(t.subtotal - t.desconto + t.frete + d[8]); t.financeiro = financials(t, items.map(i => ({ custoFabrica: i.produto.custo, qtd: i.qtd })), pagamento); }
            const created = daysAgo(d[6]);
            const hist = [{ status: 'Pago', em: created }];
            const order = {
                id: 'GU-' + String(d[0]).padStart(4, '0'), num: d[0], userId: u.id, clienteNome: u.nome,
                criadoEm: created, pagoEm: created, status: d[5], entrega, pagamento: Object.assign({}, pagamento, { juros: t.juros }),
                itens: items.map(i => ({ produtoId: i.produto.id, nome: i.produto.nome, marca: i.produto.marca, imagem: i.produto.imagem, modalidade: i.produto.modalidade, tipoVenda: i.produto.tipoVenda, tipoKit: i.produto.tipoKit || null, itensKit: i.produto.itensKit, qtd: i.qtd, precoPraticado: i.produto.precoBase, custoFabrica: i.produto.custo })),
                subtotal: t.subtotal, desconto: t.desconto, cupom: null, frete: t.frete, juros: t.juros, total: t.total,
                financeiro: t.financeiro, entregueEm: d[7] != null ? daysAgo(d[7]) : null, historico: hist
            };
            if (d[5] === 'Enviado') order.historico.push({ status: 'Em Separação', em: created }, { status: 'Enviado', em: created });
            if (d[5] === 'Entregue') order.historico.push({ status: 'Em Separação', em: created }, { status: 'Enviado', em: created }, { status: 'Entregue', em: order.entregueEm });
            orders.push(order);
        });
        return orders;
    }

    function ensureSeed() {
        if (read('seeded', null)) return;
        const users = seedUsers();
        const products = seedProducts();
        write('users', users);
        write('products', products);
        write('coupons', seedCoupons());
        write('orders', seedOrders(users, products));
        write('returns', []);
        write('carts', {});
        write('settings', { maintenance: false });
        write('seq', { user: 5, product: 13, order: 1048, address: 2, ret: 0 });
        write('seeded', { versao: 1, em: nowISO() });
    }

    S.reset = function () {
        Object.keys(localStorage).filter(k => k.indexOf(C.storagePrefix) === 0).forEach(k => localStorage.removeItem(k));
        ensureSeed();
    };

    function nextId(kind) {
        const seq = read('seq', {});
        seq[kind] = (seq[kind] || 0) + 1;
        write('seq', seq);
        return seq[kind];
    }

    /* ===================== sessão e contas ===================== */
    const getUsers = () => read('users', []);
    const saveUsers = (u) => write('users', u);

    S.listUsers = () => getUsers();
    S.getUser = (id) => getUsers().find(u => u.id === id) || null;
    S.currentUser = function () {
        const s = read('session', null);
        if (!s) return null;
        const u = S.getUser(s.userId);
        return u && u.ativo ? u : null;
    };
    S.isAdmin = () => { const u = S.currentUser(); return !!u && u.role === 'ADMIN'; };

    /** Função para pegar o contato da Domingas e gerar o link do WhatsApp */
    S.getAdminContact = function() {
        const admin = getUsers().find(u => u.role === 'ADMIN');
        if (!admin || !admin.telefones[0]) return { nome: 'Domingas', telefone: '' };
        
        // Limpa o telefone para deixar só números (ex: 61999990000)
        const numeroLimpo = admin.telefones[0].replace(/\D/g, '');
        // Adiciona o 55 (Brasil)
        const whatsapp = '55' + numeroLimpo;
        
        return { nome: admin.nome.split(' ')[0], telefone: whatsapp };
    };

    S.login = function (email, senha) {
        const u = getUsers().find(x => x.email.toLowerCase() === String(email || '').trim().toLowerCase());
        if (!u || !u.ativo || u.senhaHash !== U.hash(senha || '')) {
            return { ok: false, message: 'E-mail ou senha incorretos.' };
        }
        write('session', { userId: u.id, em: nowISO() });
        return { ok: true, user: u };
    };

    S.logout = function () { try { localStorage.removeItem(C.storagePrefix + 'session'); } catch (e) { /* ignore */ } };

    S.register = function (data) {
        const errors = {};
        const nome = String(data.nome || '').trim();
        const email = String(data.email || '').trim().toLowerCase();
        const birth = U.parseDateBR(data.nascimento);
        const phones = (data.telefones || []).map(t => String(t || '').trim()).filter(Boolean);

        if (nome.split(/\s+/).length < 2 || nome.length < 5) errors.nome = 'Informe seu nome completo.';
        if (!birth) errors.nascimento = 'Informe uma data válida (dd/mm/aaaa).';
        else if (birth > new Date()) errors.nascimento = 'A data de nascimento não pode estar no futuro.';
        else if (U.calcAge(birth) < C.minAge) errors.nascimento = 'É necessário ter 18 anos ou mais para criar a conta.';
        if (!U.isEmail(email)) errors.email = 'Informe um e-mail válido.';
        else if (getUsers().some(u => u.email.toLowerCase() === email)) errors.email = 'Este e-mail já está cadastrado.';
        if (!phones.length || !phones.every(U.isPhone)) errors.telefone = 'Informe um telefone válido com DDD.';
        if (!U.isStrongPassword(data.senha)) errors.senha = 'A senha não atende a todos os requisitos de segurança.';
        if (!data.aceiteTermos) errors.termos = 'Você precisa declarar a maioridade e aceitar os Termos de Uso.';
        if (Object.keys(errors).length) return { ok: false, errors };

        const user = {
            id: nextId('user'), nome, nascimento: data.nascimento, email, telefones: phones,
            senhaHash: U.hash(data.senha), role: 'CLIENT', ativo: true, enderecos: [], criadoEm: nowISO(),
            aceiteTermos: { dataHora: nowISO(), versao: C.termsVersion }
        };
        const users = getUsers(); users.push(user); saveUsers(users);
        return { ok: true, user };
    };

    S.updateProfile = function (userId, data) {
        const users = getUsers();
        const u = users.find(x => x.id === userId);
        if (!u) return { ok: false, message: 'Usuária não encontrada.' };
        const errors = {};
        const nome = String(data.nome || '').trim();
        const email = String(data.email || '').trim().toLowerCase();
        const birth = U.parseDateBR(data.nascimento);
        const phones = (data.telefones || []).map(t => String(t || '').trim()).filter(Boolean);
        if (nome.split(/\s+/).length < 2) errors.nome = 'Informe seu nome completo.';
        if (!birth) errors.nascimento = 'Informe uma data válida (dd/mm/aaaa).';
        else if (U.calcAge(birth) < C.minAge) errors.nascimento = 'É necessário ter 18 anos ou mais.';
        if (!U.isEmail(email)) errors.email = 'Informe um e-mail válido.';
        else if (users.some(x => x.id !== userId && x.email.toLowerCase() === email)) errors.email = 'Este e-mail já está em uso por outra conta.';
        if (!phones.length) errors.telefone = 'Mantenha ao menos um telefone.';
        else if (!phones.every(U.isPhone)) errors.telefone = 'Confira os telefones: use DDD + número.';
        if (Object.keys(errors).length) return { ok: false, errors };
        Object.assign(u, { nome, email, nascimento: data.nascimento, telefones: phones });
        saveUsers(users);
        return { ok: true, user: u };
    };

    /* ---- endereços ---- */
    S.saveAddress = function (userId, addr) {
        const users = getUsers();
        const u = users.find(x => x.id === userId);
        if (!u) return { ok: false };
        if (addr.id) {
            const i = u.enderecos.findIndex(a => a.id === addr.id);
            if (i >= 0) u.enderecos[i] = Object.assign(u.enderecos[i], addr);
        } else {
            addr.id = nextId('address');
            addr.principal = !u.enderecos.length;
            u.enderecos.push(addr);
        }
        saveUsers(users);
        return { ok: true, address: addr };
    };
    S.removeAddress = function (userId, addrId) {
        const users = getUsers();
        const u = users.find(x => x.id === userId);
        if (!u) return;
        const wasMain = (u.enderecos.find(a => a.id === addrId) || {}).principal;
        u.enderecos = u.enderecos.filter(a => a.id !== addrId);
        if (wasMain && u.enderecos[0]) u.enderecos[0].principal = true;
        saveUsers(users);
    };
    S.setDefaultAddress = function (userId, addrId) {
        const users = getUsers();
        const u = users.find(x => x.id === userId);
        if (!u) return;
        u.enderecos.forEach(a => { a.principal = a.id === addrId; });
        saveUsers(users);
    };

    /* ---- recuperação de senha (RF01) - ALTERADO PARA OTP ---- */
    S.requestPasswordReset = function (email) {
        const u = getUsers().find(x => x.email.toLowerCase() === String(email || '').trim().toLowerCase() && x.ativo);
        if (!u) return { ok: true, found: false };
        
        // Gera um código de 6 dígitos
        const otp = Math.floor(100000 + Math.random() * 900000).toString(); 
        
        const list = read('recovery', []).filter(r => r.userId !== u.id);
        list.push({ otp, userId: u.id, expiraEm: new Date(Date.now() + 15 * 60000).toISOString(), utilizado: false });
        write('recovery', list);
        return { ok: true, found: true, otp }; 
    };
    S.getRecovery = function (otp) {
        const r = read('recovery', []).find(x => x.otp === otp);
        if (!r || r.utilizado || new Date(r.expiraEm) < new Date()) return null;
        return r;
    };
    S.resetPassword = function (otp, senha) {
        const r = S.getRecovery(otp);
        if (!r) return { ok: false, message: 'Código inválido ou expirado. Solicite um novo.' };
        if (!U.isStrongPassword(senha)) return { ok: false, message: 'A senha não atende aos requisitos de segurança.' };
        const users = getUsers();
        const u = users.find(x => x.id === r.userId);
        u.senhaHash = U.hash(senha);
        saveUsers(users);
        const list = read('recovery', []); list.forEach(x => { if (x.otp === otp) x.utilizado = true; });
        write('recovery', list);
        return { ok: true };
    };

    /* ---- configurações ---- */
    S.isMaintenance = () => !!(read('settings', {}) || {}).maintenance;
    S.setMaintenance = function (on) { const s = read('settings', {}) || {}; s.maintenance = !!on; write('settings', s); };

    /* ===================== produtos ===================== */
    const getProducts = () => read('products', []);
    const saveProducts = (p) => write('products', p);

    S.listProducts = function (opts) {
        opts = opts || {};
        return getProducts().filter(p => opts.includeInactive || p.ativo);
    };
    S.getProduct = (id) => getProducts().find(p => p.id === Number(id)) || null;

    S.isOnSale = function (p) {
        return p.precoPromocional != null && p.dataFimPromocao && U.toISODate(new Date()) <= p.dataFimPromocao;
    };
    S.effectivePrice = (p) => S.isOnSale(p) ? p.precoPromocional : p.precoBase;
    /* ===================== KITS (RF03 / DC02) =====================
       Kit PRONTO  (tipoKit 'FABRICA'): é um produto como outro qualquer, com estoque próprio.
       Kit MANUAL  (tipoKit 'MANUAL') : não tem estoque próprio; é uma lista de itens avulsos
                                         (itensKit = [{ produtoId, qtd }]) e cada venda baixa esses itens. */
    // Aceita produto, item de pedido ou item congelado (o `|| itensKit` cobre dados antigos sem tipoKit).
    const isManualKit = (x) => !!x && x.tipoVenda === 'kit' &&
        (x.tipoKit === 'MANUAL' || (!x.tipoKit && Array.isArray(x.itensKit) && x.itensKit.length > 0));
    S.isManualKit = isManualKit;

    // O que realmente sai do estoque quando este item (produto ou kit) é vendido: [{ produtoId, qtd }]
    function stockTargets(it) {
        if (it.modalidade !== 'PRONTA_ENTREGA') return [];
        if (isManualKit(it)) return (it.itensKit || []).map(c => ({ produtoId: c.produtoId, qtd: c.qtd * it.qtd }));
        return [{ produtoId: it.produtoId, qtd: it.qtd }];
    }

    function kitCost(itens, all) {
        return U.round2((itens || []).reduce((sum, c) => {
            const comp = all.find(x => x.id === c.produtoId);
            return sum + (comp ? comp.custo * c.qtd : 0);
        }, 0));
    }
    // O custo do kit manual é sempre a soma dos custos dos itens (mantém o lucro real correto)
    function refreshKitCosts(all) {
        all.forEach(k => { if (isManualKit(k) && k.itensKit) k.custo = kitCost(k.itensKit, all); });
    }

    S.kitComponents = function (p) {
        const all = getProducts();
        return (p.itensKit || []).map(c => ({ qtd: c.qtd, produto: all.find(x => x.id === c.produtoId) })).filter(c => c.produto);
    };
    S.kitsUsing = function (productId) {
        return getProducts().filter(k => k.ativo && isManualKit(k) && (k.itensKit || []).some(c => c.produtoId === Number(productId)));
    };
    // Resumo para a tela de montagem: custo, soma dos preços avulsos e quantos kits dá para vender agora
    S.kitSummary = function (itens) {
        const all = getProducts();
        const precoAvulso = U.round2((itens || []).reduce((sum, c) => {
            const comp = all.find(x => x.id === Number(c.produtoId));
            return sum + (comp ? comp.precoBase * c.qtd : 0);
        }, 0));
        return {
            custo: kitCost(itens, all), precoAvulso,
            disponivel: (itens && itens.length) ? S.available({ tipoVenda: 'kit', tipoKit: 'MANUAL', modalidade: 'PRONTA_ENTREGA', itensKit: itens }) : 0
        };
    };

    S.available = function (p) {
        if (isManualKit(p)) {
            // Kit manual: quantos kits completos os componentes permitem montar (o gargalo manda)
            if (!p.itensKit || !p.itensKit.length) return 0;
            const all = getProducts();
            return p.itensKit.reduce((min, c) => {
                const comp = all.find(x => x.id === Number(c.produtoId));
                const free = comp && comp.ativo && comp.modalidade === 'PRONTA_ENTREGA'
                    ? Math.max(0, (comp.estoque || 0) - (comp.reservado || 0)) : 0;
                return Math.min(min, Math.floor(free / Math.max(1, c.qtd)));
            }, Infinity);
        }
        if (p.modalidade === 'SOB_ENCOMENDA') return null;
        return Math.max(0, (p.estoque || 0) - (p.reservado || 0));
    };

    function normalizeKitItems(raw, selfId, all) {
        const list = Array.isArray(raw) ? raw : [];
        if (!list.length) return { error: 'Adicione ao menos um produto à composição do kit.' };
        const seen = {}, items = [];
        for (const c of list) {
            const produtoId = Number(c.produtoId), qtd = Number(c.qtd);
            const p = all.find(x => x.id === produtoId);
            if (!p || !p.ativo) return { error: 'Um dos itens do kit não existe mais ou está inativo.' };
            if (selfId && produtoId === Number(selfId)) return { error: 'Um kit não pode conter a si mesmo.' };
            if (p.tipoVenda === 'kit') return { error: `"${p.nome}" é um kit. Monte o kit apenas com produtos avulsos.` };
            if (p.modalidade !== 'PRONTA_ENTREGA') return { error: `"${p.nome}" é sob encomenda e não tem estoque para compor um kit manual.` };
            if (!Number.isInteger(qtd) || qtd < 1) return { error: `Informe uma quantidade válida para "${p.nome}".` };
            if (seen[produtoId]) return { error: `"${p.nome}" aparece mais de uma vez. Some as quantidades em uma linha só.` };
            seen[produtoId] = true;
            items.push({ produtoId, qtd });
        }
        return { items };
    }

    S.saveProduct = function (data) {
        const errors = {};
        const nome = String(data.nome || '').trim();
        const marca = String(data.marca || '').trim();
        const descricao = String(data.descricao || '').trim();
        const products = getProducts();

        // KITS: tipoKit só existe quando o tipo de venda é "kit"
        const kit = data.tipoVenda === 'kit';
        const tipoKit = kit ? (data.tipoKit === 'MANUAL' ? 'MANUAL' : 'FABRICA') : null;
        const manual = tipoKit === 'MANUAL';
        // Kit manual não tem estoque/validade próprios: depende dos componentes (sempre pronta-entrega)
        const modalidade = manual ? 'PRONTA_ENTREGA' : data.modalidade;
        const pronta = modalidade === 'PRONTA_ENTREGA';
        const controlaEstoque = pronta && !manual;

        if (nome.length < 3) errors.nome = 'Informe o nome do produto.';
        if (!marca) errors.marca = 'Informe a marca.';
        if (!data.categoria) errors.categoria = 'Selecione a categoria.';
        if (descricao.length < 10) errors.descricao = 'Informe a descrição do produto (mínimo 10 caracteres).';
        if (!manual && (!(data.custo >= 0) || isNaN(data.custo))) errors.custo = 'Informe o custo de fábrica.';
        if (!(data.precoBase > 0) || isNaN(data.precoBase)) errors.precoBase = 'Informe o preço base.';
        if (!data.tipoVenda) errors.tipoVenda = 'Selecione o tipo de venda.';
        if (controlaEstoque) {
            if (data.estoque === null || data.estoque === undefined || isNaN(data.estoque) || data.estoque < 0 || !Number.isInteger(data.estoque)) errors.estoque = 'Informe a quantidade em estoque (obrigatória na pronta-entrega).';
            if (!data.validade) errors.validade = 'Informe o vencimento do lote.';
            else { const d = U.parseDateBR(data.validade); if (!d) errors.validade = 'Data inválida (dd/mm/aaaa).'; }
        }
        let itensKit = null;
        if (manual) {
            const r = normalizeKitItems(data.itensKit, data.id, products);
            if (r.error) errors.itensKit = r.error; else itensKit = r.items;
        }
        // Um produto que já compõe kit manual não pode virar kit nem sair da pronta-entrega
        if (data.id) {
            const usedBy = S.kitsUsing(data.id);
            if (usedBy.length && (kit || modalidade !== 'PRONTA_ENTREGA')) {
                const msg = `Este produto compõe o kit manual "${usedBy[0].nome}"${usedBy.length > 1 ? ' e outros' : ''}. Tire-o do kit antes de mudar.`;
                if (kit) errors.tipoVenda = msg; else errors.modalidade = msg;
            }
        }
        if (data.promoAtiva) {
            if (!(data.precoPromocional > 0) || isNaN(data.precoPromocional)) errors.precoPromocional = 'Informe o preço promocional.';
            else if (data.precoPromocional >= data.precoBase) errors.precoPromocional = 'O preço promocional deve ser menor que o preço base.';
            const fim = U.parseDateBR(data.dataFimPromocao);
            if (!fim) errors.dataFimPromocao = 'Informe o fim da promoção (dd/mm/aaaa).';
            else if (U.toISODate(fim) < U.toISODate(new Date())) errors.dataFimPromocao = 'A data final já passou.';
        }
        if (Object.keys(errors).length) return { ok: false, errors };

        let p = data.id ? products.find(x => x.id === Number(data.id)) : null;
        const isNew = !p;
        if (isNew) { p = { id: nextId('product'), reservado: 0, ativo: true, usoMedioDias: 90 }; products.push(p); }
        const validadeDate = controlaEstoque ? U.parseDateBR(data.validade) : null;
        Object.assign(p, {
            nome, marca, categoria: data.categoria, descricao, tipoVenda: data.tipoVenda, modalidade,
            tipoKit, itensKit,
            custo: manual ? kitCost(itensKit, products) : U.round2(data.custo), precoBase: U.round2(data.precoBase),
            precoPromocional: data.promoAtiva ? U.round2(data.precoPromocional) : null,
            dataFimPromocao: data.promoAtiva ? U.toISODate(U.parseDateBR(data.dataFimPromocao)) : null,
            estoque: controlaEstoque ? data.estoque : null,
            validade: validadeDate ? U.toISODate(validadeDate) : null,
            atualizadoEm: nowISO()
        });
        if (manual) p.reservado = 0;
        refreshKitCosts(products); // se o custo de um item mudou, os kits manuais que o usam acompanham
        if (data.imagem) p.imagem = data.imagem; else if (isNew) p.imagem = IMG.a;
        if (!saveProducts(products)) return { ok: false, message: 'Não foi possível salvar: o armazenamento do navegador está cheio. Use uma imagem menor.' };
        return { ok: true, product: p, isNew };
    };

    S.removeProduct = function (id) {
        const products = getProducts();
        const p = products.find(x => x.id === Number(id));
        if (!p) return false;
        if (S.kitsUsing(p.id).length) return false; // compõe kit manual ativo: tire do kit (ou inative o kit) antes
        p.ativo = false; p.deletedAt = nowISO();
        saveProducts(products);
        const carts = read('carts', {});
        Object.keys(carts).forEach(k => { carts[k].items = carts[k].items.filter(i => i.produtoId !== p.id); });
        write('carts', carts);
        return true;
    };

    S.runPromotionCycle = function () {
        const products = getProducts();
        const today = U.toISODate(new Date());
        let changed = false;
        products.forEach(p => {
            if (p.dataFimPromocao && p.dataFimPromocao < today) { p.precoPromocional = null; p.dataFimPromocao = null; changed = true; }
        });
        if (changed) saveProducts(products);
    };

    /* ===================== frete / localização ===================== */
    S.checkDelivery = function (addr) {
        const pais = U.normalize(addr.pais || 'Brasil');
        if (pais && pais !== 'brasil') return { allowed: false, reason: 'Não realizamos envios para fora do Brasil.' };
        if (String(addr.uf || '').toUpperCase() !== 'DF') return { allowed: false, reason: 'Entrega em casa disponível apenas no Distrito Federal.' };
        const bairro = U.normalize(addr.bairro).replace(/[^a-z ]/g, '').replace(/\s+/g, ' ').trim();
        if (C.allowedNeighborhoods.indexOf(bairro) >= 0) return { allowed: true };
        if (/parque/.test(bairro)) return { allowed: false, reason: 'Itapoã Parque e Paranoá Parque não têm entrega em casa. Escolha Retirada Grátis.' };
        return { allowed: false, reason: 'Entrega em casa disponível apenas em Itapoã e Paranoá. Escolha Retirada Grátis.' };
    };

    /* ===================== cupons ===================== */
    S.validateCoupon = function (code, subtotal) {
        code = String(code || '').trim().toUpperCase();
        if (!code) return { ok: false, message: 'Digite o código do cupom.' };
        const c = read('coupons', []).find(x => x.codigo === code);
        const today = U.toISODate(new Date());
        if (!c || !c.ativo) return { ok: false, message: 'Cupom inválido.' };
        if (today < c.inicio || today > c.fim) return { ok: false, message: 'Este cupom expirou.' };
        if (subtotal < c.minimo) return { ok: false, message: `Este cupom vale para compras a partir de ${U.money(c.minimo)}.` };
        const desconto = U.round2(c.percentual ? subtotal * c.percentual / 100 : Math.min(c.valor, subtotal));
        return { ok: true, cupom: c, desconto };
    };

    /* ===================== carrinho ===================== */
    function cartKey() { const u = S.currentUser(); return u ? String(u.id) : null; }
    function getCartRaw() {
        const k = cartKey(); if (!k) return { items: [], cupom: null };
        const carts = read('carts', {});
        return carts[k] || { items: [], cupom: null };
    }
    function saveCartRaw(cart) {
        const k = cartKey(); if (!k) return;
        const carts = read('carts', {}); carts[k] = cart; write('carts', carts);
    }

    S.cartCount = () => getCartRaw().items.reduce((n, i) => n + i.qtd, 0);

    S.cartAdd = function (productId, qtd) {
        qtd = qtd || 1;
        const p = S.getProduct(productId);
        if (!p || !p.ativo) return { ok: false, message: 'Produto indisponível.' };
        const cart = getCartRaw();
        const line = cart.items.find(i => i.produtoId === p.id);
        const nextQty = (line ? line.qtd : 0) + qtd;
        const av = S.available(p);
        if (av !== null && nextQty > av) {
            return { ok: false, message: av === 0 ? 'Este produto esgotou.' : `Só temos ${av} unidade(s) de "${p.nome}" disponível(is).` };
        }
        if (line) line.qtd = nextQty; else cart.items.push({ produtoId: p.id, qtd });
        saveCartRaw(cart);
        return { ok: true, product: p, qtd: nextQty };
    };

    S.cartSetQty = function (productId, qtd) {
        const cart = getCartRaw();
        const line = cart.items.find(i => i.produtoId === productId);
        const p = S.getProduct(productId);
        if (!line || !p) return { ok: false };
        if (qtd < 1) { cart.items = cart.items.filter(i => i !== line); saveCartRaw(cart); return { ok: true, removed: true }; }
        const av = S.available(p);
        if (av !== null && qtd > av) return { ok: false, message: `Só temos ${av} unidade(s) disponível(is).`, max: av };
        line.qtd = qtd; saveCartRaw(cart);
        return { ok: true };
    };
    S.cartRemove = function (productId) { const c = getCartRaw(); c.items = c.items.filter(i => i.produtoId !== productId); saveCartRaw(c); };
    S.cartClear = function () { saveCartRaw({ items: [], cupom: null }); };

    S.cartLines = function () {
        const cart = getCartRaw();
        return cart.items.map(i => {
            const p = S.getProduct(i.produtoId);
            const av = p ? S.available(p) : 0;
            const issue = !p || !p.ativo ? 'unavailable' : (av !== null && av < i.qtd ? (av === 0 ? 'soldout' : 'reduced') : null);
            const unit = p ? S.effectivePrice(p) : 0;
            return { produtoId: i.produtoId, qtd: i.qtd, produto: p, unit, subtotal: U.round2(unit * i.qtd), disponivel: av, issue };
        }).filter(l => l.produto);
    };

    S.cartReconcile = function () {
        const cart = getCartRaw();
        const msgs = [];
        cart.items = cart.items.filter(i => {
            const p = S.getProduct(i.produtoId);
            if (!p || !p.ativo) { msgs.push('Um item foi removido da sacola porque não está mais disponível.'); return false; }
            const av = S.available(p);
            if (av !== null && av === 0) { msgs.push(`"${p.nome}" esgotou e saiu da sacola.`); return false; }
            if (av !== null && i.qtd > av) { msgs.push(`A quantidade de "${p.nome}" foi ajustada para ${av} (estoque disponível).`); i.qtd = av; }
            return true;
        });
        saveCartRaw(cart);
        return msgs;
    };

    S.cartTotals = function () {
        const lines = S.cartLines();
        const subtotal = U.round2(lines.reduce((s, l) => s + l.subtotal, 0));
        const cart = getCartRaw();
        let desconto = 0, cupom = null;
        if (cart.cupom) {
            const r = S.validateCoupon(cart.cupom, subtotal);
            if (r.ok) { desconto = r.desconto; cupom = r.cupom.codigo; } else { cart.cupom = null; saveCartRaw(cart); }
        }
        return { lines, subtotal, desconto, cupom, total: U.round2(subtotal - desconto) };
    };

    S.applyCoupon = function (code) {
        const t = S.cartTotals();
        if (!t.lines.length) return { ok: false, message: 'Adicione produtos à sacola antes de aplicar um cupom.' };
        const r = S.validateCoupon(code, t.subtotal);
        if (!r.ok) return r;
        const cart = getCartRaw(); cart.cupom = r.cupom.codigo; saveCartRaw(cart);
        return r;
    };
    S.removeCoupon = function () { const c = getCartRaw(); c.cupom = null; saveCartRaw(c); };

    /* ===================== pedidos ===================== */
    const getOrders = () => read('orders', []);
    const saveOrders = (o) => write('orders', o);
    const SALE_STATUS = ['Pago', 'Em Separação', 'Enviado', 'Entregue', 'Devolução solicitada'];
    S.STATUS_FLOW = ['Pago', 'Em Separação', 'Enviado', 'Entregue'];
    S.statusClass = function (status) {
        return ({
            'Aguardando pagamento': 'pendente', 'Pago': 'pago', 'Em Separação': 'separacao', 'Enviado': 'enviado',
            'Entregue': 'entregue', 'Cancelado': 'cancelado', 'Devolução solicitada': 'devolucao', 'Devolvido': 'devolvido'
        })[status] || 'pago';
    };

    function computeTotals(items, cupomResult, tipoEntrega, pagamento) {
        const subtotal = U.round2(items.reduce((s, i) => s + i.precoPraticado * i.qtd, 0));
        const desconto = cupomResult ? cupomResult.desconto : 0;
        const frete = tipoEntrega === 'home' ? C.deliveryFee : 0;
        const base = U.round2(subtotal - desconto + frete);
        const parcelas = pagamento.metodo === 'CARTAO' ? (pagamento.parcelas || 1) : 1;
        const juros = pagamento.metodo === 'CARTAO' ? U.round2(base * (C.installmentInterest[parcelas] || 0)) : 0;
        const t = { subtotal, desconto, frete, juros, total: U.round2(base + juros) };
        t.financeiro = financials(t, items, pagamento);
        return t;
    }
    function financials(t, items, pagamento) {
        const valorVenda = U.round2(t.subtotal - t.desconto);
        const custo = U.round2(items.reduce((s, i) => s + i.custoFabrica * i.qtd, 0));
        const taxa = U.round2((valorVenda + t.frete) * (C.gatewayFees[pagamento.metodo] || 0));
        return { valorVenda, custoFabrica: custo, taxaGateway: taxa, juros: t.juros, lucroReal: U.round2(valorVenda - custo - taxa) };
    }

    S.previewTotals = function (tipoEntrega, pagamento) {
        const t = S.cartTotals();
        const coupon = t.cupom ? { desconto: t.desconto } : null;
        const items = t.lines.map(l => ({ precoPraticado: l.unit, custoFabrica: l.produto.custo, qtd: l.qtd }));
        return Object.assign({ lines: t.lines, cupom: t.cupom }, computeTotals(items, coupon, tipoEntrega, pagamento));
    };

    S.getOrder = (id) => getOrders().find(o => o.id === id) || null;
    S.listOrders = function (opts) {
        opts = opts || {};
        return getOrders().filter(o => !opts.userId || o.userId === opts.userId)
            .sort((a, b) => new Date(b.criadoEm) - new Date(a.criadoEm));
    };

    S.pixCode = function (order) {
        const amt = order.total.toFixed(2);
        const body = '00020126580014br.gov.bcb.pix0136' + (order.pagamento.pixChave || '') +
            '52040000530398654' + String(amt.length).padStart(2, '0') + amt + '5802BR5906GLOWUP6008BRASILIA62070503***6304';
        let crc = 0; for (let i = 0; i < body.length; i++) crc = (crc * 31 + body.charCodeAt(i)) & 0xffff;
        return body + crc.toString(16).toUpperCase().padStart(4, '0');
    };

    S.createOrder = function (payload) {
        const user = S.currentUser();
        if (!user) return { ok: false, message: 'Faça login para finalizar o pedido.' };
        const birth = U.parseDateBR(user.nascimento);
        if (!birth || U.calcAge(birth) < C.minAge) return { ok: false, message: 'Compras são permitidas apenas para maiores de 18 anos.' };

        const metodo = payload.pagamento && payload.pagamento.metodo;
        if (['PIX', 'CARTAO'].indexOf(metodo) < 0) return { ok: false, message: 'Forma de pagamento não aceita. Use Pix ou cartão de crédito.' };
        const parcelas = metodo === 'CARTAO' ? Number(payload.pagamento.parcelas || 1) : 1;
        if (parcelas < 1 || parcelas > C.maxInstallments) return { ok: false, message: `Parcelamento máximo: ${C.maxInstallments}x.` };

        const tipo = payload.entrega && payload.entrega.tipo;
        if (['home', 'pickup'].indexOf(tipo) < 0) return { ok: false, message: 'Escolha como quer receber.' };
        if (tipo === 'home') {
            const chk = S.checkDelivery(payload.entrega.endereco || {});
            if (!chk.allowed) return { ok: false, field: 'cep', message: chk.reason };
        }

        const cartTotals = S.cartTotals();
        if (!cartTotals.lines.length) return { ok: false, message: 'Sua sacola está vazia.' };
        const bad = cartTotals.lines.find(l => l.issue);
        if (bad) return { ok: false, stock: true, message: `"${bad.produto.nome}" não tem estoque suficiente. Revise a sacola.` };

        // Kit manual + o mesmo item avulso na sacola disputam o mesmo estoque: confere a demanda somada
        const demand = {};
        cartTotals.lines.forEach(l => stockTargets({
            produtoId: l.produto.id, qtd: l.qtd, modalidade: l.produto.modalidade,
            tipoVenda: l.produto.tipoVenda, tipoKit: l.produto.tipoKit, itensKit: l.produto.itensKit
        }).forEach(t => { demand[t.produtoId] = (demand[t.produtoId] || 0) + t.qtd; }));
        const short = Object.keys(demand).map(id => S.getProduct(id)).find(pr => pr && demand[pr.id] > Math.max(0, (pr.estoque || 0) - (pr.reservado || 0)));
        if (short) return { ok: false, stock: true, message: `"${short.nome}" não tem estoque suficiente para tudo o que está na sacola (inclui itens de kits). Revise a sacola.` };

        const items = cartTotals.lines.map(l => ({
            produtoId: l.produto.id, nome: l.produto.nome, marca: l.produto.marca, imagem: l.produto.imagem,
            modalidade: l.produto.modalidade, 
            tipoVenda: l.produto.tipoVenda, // Congela o tipo de venda
            tipoKit: l.produto.tipoKit || null, // Congela o tipo de kit (FABRICA/MANUAL)
            itensKit: l.produto.itensKit ? JSON.parse(JSON.stringify(l.produto.itensKit)) : null, // Congela os itens do kit
            qtd: l.qtd,
            precoPraticado: l.unit, custoFabrica: l.produto.custo
        }));
        const coupon = cartTotals.cupom ? { desconto: cartTotals.desconto } : null;
        const pag = { metodo, parcelas };
        const t = computeTotals(items, coupon, tipo, pag);

        const products = getProducts();
        items.forEach(it => {
            // Reserva o próprio produto (ou kit pronto) ou, no kit manual, cada componente
            stockTargets(it).forEach(t => {
                const p = products.find(x => x.id === t.produtoId);
                if (p) p.reservado = (p.reservado || 0) + t.qtd;
            });
        });
        saveProducts(products);

        const num = nextId('order');
        const order = {
            id: 'GU-' + String(num).padStart(4, '0'), num, userId: user.id, clienteNome: user.nome,
            criadoEm: nowISO(), pagoEm: null, status: 'Aguardando pagamento',
            entrega: { tipo, prazoDias: C.deliveryDays, endereco: tipo === 'home' ? payload.entrega.endereco : null },
            pagamento: { metodo, parcelas, juros: t.juros, pixChave: U.randomToken(32) },
            itens: items, subtotal: t.subtotal, desconto: t.desconto, cupom: cartTotals.cupom,
            frete: t.frete, juros: t.juros, total: t.total, financeiro: t.financeiro, entregueEm: null,
            historico: [{ status: 'Aguardando pagamento', em: nowISO() }],
            expiraEm: metodo === 'PIX' ? new Date(Date.now() + C.pixExpiresMinutes * 60000).toISOString() : null
        };
        const orders = getOrders(); orders.push(order); saveOrders(orders);
        return { ok: true, order };
    };

    function setStatus(order, status) {
        order.status = status;
        order.historico = order.historico || [];
        order.historico.push({ status, em: nowISO() });
    }

    S.approvePayment = function (orderId) {
        const orders = getOrders();
        const o = orders.find(x => x.id === orderId);
        if (!o) return { ok: false, message: 'Pedido não encontrado.' };
        if (o.status !== 'Aguardando pagamento') return { ok: true, ignored: true, order: o };
        
        const products = getProducts();
        o.itens.forEach(it => {
            // RF03: kit manual baixa cada componente; kit pronto e produto avulso baixam o próprio estoque
            stockTargets(it).forEach(t => {
                const p = products.find(x => x.id === t.produtoId);
                if (p) { p.estoque = Math.max(0, (p.estoque || 0) - t.qtd); p.reservado = Math.max(0, (p.reservado || 0) - t.qtd); }
            });
        });
        saveProducts(products);
        
        o.pagoEm = nowISO();
        setStatus(o, 'Pago');
        saveOrders(orders);
        
        const carts = read('carts', {}); if (carts[String(o.userId)]) { carts[String(o.userId)] = { items: [], cupom: null }; write('carts', carts); }
        return { ok: true, order: o };
    };

    S.rejectPayment = function (orderId) {
        const orders = getOrders();
        const o = orders.find(x => x.id === orderId);
        if (!o || o.status !== 'Aguardando pagamento') return { ok: false };
        
        const products = getProducts();
        o.itens.forEach(it => {
            stockTargets(it).forEach(t => {
                const p = products.find(x => x.id === t.produtoId);
                if (p) p.reservado = Math.max(0, (p.reservado || 0) - t.qtd);
            });
        });
        saveProducts(products);
        
        setStatus(o, 'Cancelado');
        saveOrders(orders);
        return { ok: true, order: o };
    };

    S.expirePendingOrders = function () {
        getOrders().filter(o => o.status === 'Aguardando pagamento' && o.expiraEm && new Date(o.expiraEm) < new Date())
            .forEach(o => S.rejectPayment(o.id));
    };

    S.nextStatus = function (order) {
        const i = S.STATUS_FLOW.indexOf(order.status);
        return i >= 0 && i < S.STATUS_FLOW.length - 1 ? S.STATUS_FLOW[i + 1] : null;
    };

    S.advanceOrder = function (orderId) {
        if (!S.isAdmin()) return { ok: false, message: 'Apenas a administradora pode alterar o status.' };
        const orders = getOrders();
        const o = orders.find(x => x.id === orderId);
        if (!o) return { ok: false, message: 'Pedido não encontrado.' };
        const next = S.nextStatus(o);
        if (!next) return { ok: false, message: 'Este pedido não pode mais avançar.' };
        if (next === 'Entregue') o.entregueEm = nowISO();
        setStatus(o, next);
        saveOrders(orders);
        return { ok: true, order: o, status: next };
    };

    /* ===================== devoluções ===================== */
    const getReturns = () => read('returns', []);

    S.returnDeadline = function (order) {
        return order.entregueEm ? U.addDays(new Date(order.entregueEm), C.returnWindowDays) : null;
    };
    S.returnState = function (order) {
        if (order.status === 'Devolução solicitada') return 'requested';
        if (order.status === 'Devolvido') return 'done';
        if (order.devolucaoRejeitada) return 'rejected';
        if (order.status !== 'Entregue' || !order.entregueEm) return 'none';
        return new Date() <= S.returnDeadline(order) ? 'eligible' : 'expired';
    };

    S.requestReturn = function (orderId, data) {
        const user = S.currentUser();
        const orders = getOrders();
        const o = orders.find(x => x.id === orderId);
        if (!o || !user || o.userId !== user.id) return { ok: false, message: 'Pedido não encontrado.' };
        const st = S.returnState(o);
        if (st === 'expired') return { ok: false, message: 'O prazo de 7 dias para devolução já terminou.' };
        if (st !== 'eligible') return { ok: false, message: 'Este pedido não pode ser devolvido.' };
        if (!data.lacrado) return { ok: false, message: 'Só aceitamos devolução de produtos lacrados, sem sinais de uso.' };
        const list = getReturns();
        const ret = {
            id: nextId('ret'), pedidoId: o.id, userId: o.userId, clienteNome: o.clienteNome,
            motivo: data.motivo, solicitadoEm: nowISO(), status: 'SOLICITADA', resolvidoEm: null
        };
        list.push(ret); write('returns', list);
        setStatus(o, 'Devolução solicitada');
        saveOrders(orders);
        return { ok: true, ret };
    };

    S.listReturns = () => getReturns().sort((a, b) => new Date(b.solicitadoEm) - new Date(a.solicitadoEm));

    S.resolveReturn = function (retId, approved) {
        if (!S.isAdmin()) return { ok: false, message: 'Apenas a administradora pode analisar devoluções.' };
        const list = getReturns();
        const ret = list.find(r => r.id === retId);
        if (!ret || ret.status !== 'SOLICITADA') return { ok: false, message: 'Solicitação não encontrada.' };
        const orders = getOrders();
        const o = orders.find(x => x.id === ret.pedidoId);
        ret.status = approved ? 'APROVADA' : 'REJEITADA';
        ret.resolvidoEm = nowISO();
        
        if (approved) {
            const products = getProducts();
            o.itens.forEach(it => {
                stockTargets(it).forEach(t => {
                    const p = products.find(x => x.id === t.produtoId);
                    if (p) p.estoque = (p.estoque || 0) + t.qtd;
                });
            });
            saveProducts(products);
            setStatus(o, 'Devolvido');
        } else {
            o.devolucaoRejeitada = true;
            setStatus(o, 'Entregue');
        }
        write('returns', list); saveOrders(orders);
        return { ok: true, ret, order: o };
    };

    /* ===================== painel da administradora ===================== */
    S.PERIODS = ['Hoje', 'Últimos 7 dias', 'Este Mês', 'Últimos 30 dias', 'Último Ano', 'Todo o período'];

    function periodRange(key) {
        const end = new Date();
        const today = U.startOfDay(end);
        let start;
        switch (key) {
            case 'Hoje': start = today; break;
            case 'Últimos 7 dias': start = U.addDays(today, -6); break;
            case 'Este Mês': start = new Date(end.getFullYear(), end.getMonth(), 1); break;
            case 'Último Ano': start = U.addDays(today, -364); break;
            case 'Todo o período': {
                const dates = getOrders().map(o => new Date(o.criadoEm).getTime());
                start = dates.length ? U.startOfDay(new Date(Math.min.apply(null, dates))) : today; break;
            }
            default: start = U.addDays(today, -29);
        }
        return { start, end };
    }

    const isSale = (o) => SALE_STATUS.indexOf(o.status) >= 0;

    S.dashboardMetrics = function (key) {
        const { start, end } = periodRange(key);
        const inRange = (o, s, e) => { const t = new Date(o.criadoEm); return t >= s && t <= e && isSale(o); };
        const orders = getOrders();
        const cur = orders.filter(o => inRange(o, start, end));
        const sum = (arr, f) => U.round2(arr.reduce((s, o) => s + f(o), 0));
        const faturamento = sum(cur, o => o.total);
        const lucro = sum(cur, o => o.financeiro.lucroReal);

        let delta = null;
        if (key !== 'Todo o período') {
            const len = end - start;
            const prevStart = new Date(start.getTime() - len), prevEnd = new Date(start.getTime() - 1);
            const prev = orders.filter(o => inRange(o, prevStart, prevEnd)).length;
            if (prev > 0) delta = Math.round((cur.length - prev) / prev * 100);
        }

        const buckets = Array.from({ length: 7 }, () => ({ fat: 0, lucro: 0 }));
        const span = Math.max(end - start, 1);
        cur.forEach(o => {
            const i = Math.min(6, Math.floor((new Date(o.criadoEm) - start) / span * 7));
            buckets[i].fat += o.total; buckets[i].lucro += o.financeiro.lucroReal;
        });

        const prods = getProducts().filter(p => p.ativo && p.modalidade === 'PRONTA_ENTREGA' && !isManualKit(p)); // kit manual não tem estoque próprio
        return {
            vendas: cur.length, faturamento, lucro, ticket: cur.length ? U.round2(faturamento / cur.length) : 0, delta,
            estoqueTotal: prods.reduce((s, p) => s + (p.estoque || 0), 0),
            estoqueBaixo: prods.filter(p => (p.estoque || 0) <= C.lowStockThreshold).length,
            chart: buckets, recentes: S.listOrders().slice(0, 4)
        };
    };

    S.expiryAlerts = function () {
        return getProducts().filter(p => p.ativo && p.modalidade === 'PRONTA_ENTREGA' && p.validade && (p.estoque || 0) > 0)
            .map(p => ({ produto: p, dias: U.diffDays(new Date(p.validade + 'T12:00:00'), new Date()) }))
            .filter(a => a.dias <= C.expiryAlertDays)
            .sort((a, b) => a.dias - b.dias);
    };

    S.crmSuggestions = function () {
        const latest = {};
        getOrders().filter(o => ['Entregue', 'Enviado'].indexOf(o.status) >= 0).forEach(o => {
            o.itens.forEach(it => {
                const k = o.userId + ':' + it.produtoId;
                const when = new Date(o.entregueEm || o.criadoEm);
                if (!latest[k] || when > latest[k].when) latest[k] = { when, order: o, item: it };
            });
        });
        const users = getUsers();
        return Object.keys(latest).map(k => {
            const { when, order, item } = latest[k];
            const p = S.getProduct(item.produtoId);
            const uso = p && p.usoMedioDias ? p.usoMedioDias : 90;
            const date = U.addDays(when, uso);
            return { user: users.find(u => u.id === order.userId), produto: item.nome, usoMedioDias: uso, dataReposicao: date, dias: U.diffDays(date, new Date()), pedidoId: order.id };
        }).filter(s => s.user && s.dias <= C.crmWindowDays).sort((a, b) => a.dias - b.dias);
    };

    S.searchAll = function (q) {
        const n = U.normalize(q);
        if (!n) return { products: [], orders: [], clients: [] };
        return {
            products: getProducts().filter(p => p.ativo && U.normalize(p.nome + ' ' + p.marca + ' ' + p.categoria).includes(n)),
            orders: getOrders().filter(o => U.normalize(o.id + ' ' + o.clienteNome + ' ' + o.status).includes(n)),
            clients: getUsers().filter(u => u.role === 'CLIENT' && U.normalize(u.nome + ' ' + u.email).includes(n))
        };
    };

    /* ===================== inicialização ===================== */
    // Dados salvos antes do campo tipoKit existir: classifica os kits e zera o estoque próprio dos manuais
    function migrateKits() {
        const products = getProducts();
        let changed = false;
        products.forEach(p => {
            if (p.tipoVenda !== 'kit' || p.tipoKit) return;
            const manual = Array.isArray(p.itensKit) && p.itensKit.length > 0;
            p.tipoKit = manual ? 'MANUAL' : 'FABRICA';
            if (manual) { p.modalidade = 'PRONTA_ENTREGA'; p.estoque = null; p.validade = null; p.reservado = 0; }
            changed = true;
        });
        if (changed) saveProducts(products);
    }

    S.init = function () {
        ensureSeed();
        migrateKits();
        S.runPromotionCycle();
        S.expirePendingOrders();
    };

    S.init();
})(window);