/* GlowUp - comportamentos comuns a todas as telas:
   proteção de rotas (RF01/RBAC), modo manutenção, navegação, carrinho, busca, menu lateral e rodapé. */
(function (global) {
    'use strict';

    const GlowUp = (global.GlowUp = global.GlowUp || {});
    const C = GlowUp.config, U = GlowUp.utils, S = GlowUp.store, UI = GlowUp.ui;
    const R = C.routes;
    const page = GlowUp.page;
    const L = (GlowUp.layout = {});

    const kind = C.pages.admin.indexOf(page) >= 0 ? 'admin' : C.pages.client.indexOf(page) >= 0 ? 'client' : 'public';
    L.kind = kind;
    const home = (u) => (u && u.role === 'ADMIN' ? R.dashboard : R.store);

    /* ---------- proteção de rotas ---------- */
    function guard() {
        const user = S.currentUser();
        const redirect = (url, msg, type) => {
            if (msg) UI.flash(msg, { type: type || 'warning' });
            GlowUp.blocked = true;
            location.replace(url);
        };
        if (kind !== 'admin' && S.isMaintenance() && !(user && user.role === 'ADMIN') &&
            ['login', 'manutencao', 'termosdeuso', 'termodedevolucao'].indexOf(page) < 0 && kind === 'client') {
            return redirect(R.maintenance);
        }
        if (!C.guardsEnabled) return;
        if (kind === 'client' && !user) return redirect(R.login, 'Entre ou cadastre-se para ver o catálogo e comprar.', 'info');
        if (kind === 'admin' && !user) return redirect(R.login, 'Entre para acessar o painel da administradora.', 'info');
        if (kind === 'admin' && user.role !== 'ADMIN') return redirect(R.store, 'Esta área é exclusiva da administradora.', 'error');
        if (user && ['login', 'cadastro', 'recuperaracesso'].indexOf(page) >= 0) return redirect(home(user));
    }
    /* ---------- proteção de rotas ---------- */
    L.refreshCart = function () {
        // O painel da administradora não tem carrinho: evita uma chamada inútil à API em toda tela.
        if (!U.$('.cart-badge') || S.isAdmin()) return Promise.resolve();
        const paint = n => U.$$('.cart-badge').forEach(b => { b.textContent = n; });
        const key = C.storagePrefix + 'cartCount';
        try { const c = sessionStorage.getItem(key); if (c !== null) paint(c); } catch (e) { /* ignore */ }
        return Promise.resolve(S.cartCount()).then(n => {
            paint(n);
            try { sessionStorage.setItem(key, n); } catch (e) { /* ignore */ }
        });
    };

    L.logout = async function () {
        const ok = await UI.confirm({
            title: 'Sair da conta?', message: 'Você precisará entrar novamente para continuar.',
            confirmText: 'Sair', icon: UI.icons.logout
        });
        if (!ok) return;
        await S.logout();
        UI.flash('Você saiu da sua conta. Até logo!', { type: 'info', title: 'Sessão encerrada' });
        location.href = R.login;
    };

    /** Define uma função chamada quando a pessoa digita na busca do topo (telas do painel). */
    L.onSearch = null;

    function wire() {
        UI.showFlash();

        // logo
        const logoLink = U.$('a.logo-container');
        if (logoLink) logoLink.href = kind === 'admin' ? R.dashboard : kind === 'client' ? R.store : R.landing;

        // navegação da cliente
        U.$$('.nav-links a').forEach(a => {
            const t = a.textContent.trim().toLowerCase();
            if (t.indexOf('vitrine') === 0) a.href = R.store;
            else if (t.indexOf('carrinho') === 0) a.href = R.cart;
            else if (t.indexOf('pedidos') === 0) a.href = R.myOrders;
            else if (a.querySelector('svg') && !t) a.href = R.profile;
        });
        L.refreshCart();

        // menu hambúrguer (mobile)
        const navMenu = U.$('body > header .nav-links');
        if (navMenu && !U.$('.menu-toggle')) {
            const btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'menu-toggle';
            btn.setAttribute('aria-label', 'Abrir menu');
            btn.setAttribute('aria-expanded', 'false');
            btn.innerHTML = '<svg width="22" height="22" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" viewBox="0 0 24 24"><path d="M4 7h16M4 12h16M4 17h16"/></svg>';
            navMenu.before(btn);

            const setOpen = open => {
                navMenu.classList.toggle('open', open);
                btn.setAttribute('aria-expanded', String(open));
                btn.setAttribute('aria-label', open ? 'Fechar menu' : 'Abrir menu');
            };
            btn.addEventListener('click', e => { e.stopPropagation(); setOpen(!navMenu.classList.contains('open')); });
            document.addEventListener('click', e => { if (!navMenu.contains(e.target)) setOpen(false); });
            document.addEventListener('keydown', e => { if (e.key === 'Escape') setOpen(false); });
        }

        // menu lateral do painel
        const sidebarRoutes = { 'visão geral': R.dashboard, 'pedidos': R.orders, 'devoluções': R.returns, 'produtos': R.products, 'crm reposição': R.crm, 'meu perfil': R.adminProfile };
        U.$$('.sidebar-link').forEach(a => { const r = sidebarRoutes[a.textContent.trim().toLowerCase()]; if (r) a.setAttribute('href', r); });

        // menu hambúrguer da administradora (mobile)
        const adminHeader = kind === 'admin' ? U.$('body > header') : null;
        const adminSide = kind === 'admin' ? U.$('.layout-container > aside') : null;
        if (adminHeader && adminSide && !U.$('.menu-toggle-admin')) {
            adminSide.id = adminSide.id || 'admin-menu';

            const btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'menu-toggle-admin';
            btn.setAttribute('aria-label', 'Abrir menu');
            btn.setAttribute('aria-expanded', 'false');
            btn.setAttribute('aria-controls', adminSide.id);
            btn.innerHTML = '<svg width="22" height="22" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" viewBox="0 0 24 24" aria-hidden="true"><path class="bar" d="M4 7h16"/><path class="bar bar-mid" d="M4 12h16"/><path class="bar" d="M4 17h16"/></svg>';
            adminHeader.prepend(btn);

            const closeBtn = document.createElement('button');
            closeBtn.type = 'button';
            closeBtn.className = 'menu-close-admin';
            closeBtn.setAttribute('aria-label', 'Fechar menu');
            closeBtn.innerHTML = '<svg width="22" height="22" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" viewBox="0 0 24 24" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18"/></svg>';
            adminSide.prepend(closeBtn);

            const overlay = document.createElement('div');
            overlay.className = 'admin-overlay';
            overlay.setAttribute('aria-hidden', 'true');
            document.body.appendChild(overlay);

            // o anel que pulsa só aparece na primeira tela da sessão (não cansa quem navega muito)
            try {
                if (!sessionStorage.getItem('gu_admin_menu_seen')) {
                    btn.classList.add('attention');
                    sessionStorage.setItem('gu_admin_menu_seen', '1');
                }
            } catch (e) { /* sem sessionStorage: segue sem a animação de atenção */ }

            let isOpen = false;
            const setOpen = (open, restoreFocus) => {
                if (open === isOpen) return;
                isOpen = open;
                adminSide.classList.toggle('open', open);
                overlay.classList.toggle('show', open);
                document.documentElement.classList.toggle('admin-menu-open', open);
                btn.setAttribute('aria-expanded', String(open));
                btn.setAttribute('aria-label', open ? 'Fechar menu' : 'Abrir menu');
                if (open) closeBtn.focus({ preventScroll: true });
                else if (restoreFocus) btn.focus({ preventScroll: true });
            };

            btn.addEventListener('click', () => setOpen(!isOpen, true));
            closeBtn.addEventListener('click', () => setOpen(false, true));
            overlay.addEventListener('click', () => setOpen(false, true));
            document.addEventListener('keydown', e => { if (e.key === 'Escape') setOpen(false, true); });
            adminSide.addEventListener('click', e => { if (e.target.closest('.sidebar-link, .logout-btn')) setOpen(false, false); });

            // ao voltar para tela grande, garante que o menu fique fechado
            const mq = window.matchMedia('(max-width: 768px)');
            const onChange = () => { if (!mq.matches) setOpen(false, false); };
            if (mq.addEventListener) mq.addEventListener('change', onChange); else mq.addListener(onChange);
        }

        // Modo manutenção: o estado real fica no back; a chave só reflete e altera esse estado.
        const toggle = U.$('.maintenance-toggle input[type=checkbox]');
        if (toggle) {
            toggle.disabled = true;
            S.loadMaintenanceStatus()
                .then(on => { toggle.checked = on; })
                .catch(() => UI.toast('Não foi possível ler o estado do modo manutenção.', { type: 'warning' }))
                .then(() => { toggle.disabled = false; });
            toggle.addEventListener('change', async () => {
                const wanted = toggle.checked;
                toggle.disabled = true;
                const res = await S.setMaintenance(wanted);
                toggle.disabled = false;
                if (!res.ok) {
                    toggle.checked = !wanted;
                    UI.toast(res.message || 'Não foi possível alterar o modo manutenção.', { type: 'error', title: 'Modo manutenção' });
                    return;
                }
                UI.toast(wanted
                    ? 'As clientes verão a tela de manutenção até você desativar.'
                    : 'A loja voltou a funcionar normalmente.', { type: wanted ? 'warning' : 'success', title: wanted ? 'Modo manutenção ativado' : 'Modo manutenção desativado' });
            });
        }

        // sair
        U.$$('.logout-btn, .btn-logout').forEach(b => b.addEventListener('click', e => { e.preventDefault(); L.logout(); }));

        // rodapé: Termos de Uso / Política de Devolução em janela
        U.$$('.footer-links a, footer a').forEach(a => {
            if (a.id === 'open-terms') return;
            const t = U.normalize(a.textContent);
            const type = t.indexOf('termos') === 0 ? 'terms' : t.indexOf('politica') === 0 ? 'returns' : null;
            if (!type) return;
            a.setAttribute('href', type === 'terms' ? R.terms : R.returnsPolicy);
            a.addEventListener('click', e => { e.preventDefault(); UI.legal(type); });
        });

        // busca do topo
        const search = U.$('header .search-bar input');
        if (search) {
            const params = U.query('q');
            if (params && !search.value) search.value = params;
            search.addEventListener('input', U.debounce(() => { if (L.onSearch) L.onSearch(search.value.trim()); }, 160));
            search.addEventListener('keydown', async e => {
                if (e.key !== 'Enter') return;
                e.preventDefault();
                const q = search.value.trim();
                if (L.onSearch) { L.onSearch(q); return; }
                if (!q) return;
                if (kind === 'client') { location.href = R.store + '?q=' + encodeURIComponent(q); return; }
                const r = await S.searchAll(q);
                if (r.products.length) location.href = R.products + '?q=' + encodeURIComponent(q);
                else if (r.orders.length || r.clients.length) location.href = R.orders + '?q=' + encodeURIComponent(q);
                else UI.toast(`Nada encontrado para "${q}".`, { type: 'info', title: 'Busca' });
            });
            L.searchInput = search;
        }
    }

    /** Executa quando o HTML estiver pronto (e a página não tiver sido redirecionada). */
    /* O conteúdo de exemplo do HTML fica escondido (css/base/reset.css) até todas as telas terminarem de carregar.
       Telas que mostram o próprio "carregando" podem chamar GlowUp.showPage() antes. */
    let pending = 0;
    GlowUp.showPage = function () { if (document.body) document.body.classList.add('gu-ready'); };
    const checkReady = function () { if (pending === 0 && document.readyState === 'complete') GlowUp.showPage(); };
    window.addEventListener('load', checkReady);
    GlowUp.ready = function (fn) {
        if (GlowUp.blocked) return;
        pending++;
        const finish = function () { pending--; checkReady(); };
        const go = function () { Promise.resolve().then(fn).catch(function (e) { console.error(e); }).then(finish); };
        const run = function () { if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', go); else go(); };
        if (S.ready && typeof S.ready.then === 'function') S.ready.then(run, run); else run();
    };
    GlowUp.ready(function () {
        guard();
        if (!GlowUp.blocked) wire();
    });
})(window);

/* Foto que não carrega vira a imagem padrão do GlowUp (no lugar de links externos). */
document.addEventListener('error', function (e) {
    const t = e.target;
    if (t && t.tagName === 'IMG' && !t.dataset.fallback && t.getAttribute('src') !== 'assets/img/logo-flor.svg') {
        t.dataset.fallback = '1';
        t.src = GlowUp.utils.placeholderImage;
    }
}, true);
