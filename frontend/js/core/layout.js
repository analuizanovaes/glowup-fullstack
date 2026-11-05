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
        if (kind === 'admin' && !user) return redirect(R.login, 'Entre com a conta da administradora.', 'info');
        if (kind === 'admin' && user.role !== 'ADMIN') return redirect(R.store, 'Esta área é exclusiva da administradora.', 'error');
        if (user && ['login', 'cadastro', 'recuperaracesso'].indexOf(page) >= 0) return redirect(home(user));
    }
    guard();
    if (GlowUp.blocked) return;

    /* ---------- utilidades ---------- */
    L.refreshCart = function () {
        const n = S.cartCount();
        U.$$('.cart-badge').forEach(b => { b.textContent = n; });
    };

    L.logout = async function () {
        const ok = await UI.confirm({
            title: 'Sair da conta?', message: 'Você precisará entrar novamente para continuar.',
            confirmText: 'Sair', icon: UI.icons.logout
        });
        if (!ok) return;
        S.logout();
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

        // menu lateral do painel
        const sidebarRoutes = { 'visão geral': R.dashboard, 'pedidos': R.orders, 'devoluções': R.returns, 'produtos': R.products, 'crm reposição': R.crm, 'meu perfil': R.adminProfile };
        U.$$('.sidebar-link').forEach(a => { const r = sidebarRoutes[a.textContent.trim().toLowerCase()]; if (r) a.setAttribute('href', r); });

        const toggle = U.$('.maintenance-toggle input[type=checkbox]');
        if (toggle) {
            toggle.checked = S.isMaintenance();
            toggle.addEventListener('change', () => {
                S.setMaintenance(toggle.checked);
                UI.toast(toggle.checked
                    ? 'As clientes verão a tela de manutenção até você desativar.'
                    : 'A loja voltou a funcionar normalmente.', { type: toggle.checked ? 'warning' : 'success', title: toggle.checked ? 'Modo manutenção ativado' : 'Modo manutenção desativado' });
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
            search.addEventListener('keydown', e => {
                if (e.key !== 'Enter') return;
                e.preventDefault();
                const q = search.value.trim();
                if (L.onSearch) { L.onSearch(q); return; }
                if (!q) return;
                if (kind === 'client') { location.href = R.store + '?q=' + encodeURIComponent(q); return; }
                const r = S.searchAll(q);
                if (r.products.length) location.href = R.products + '?q=' + encodeURIComponent(q);
                else if (r.orders.length || r.clients.length) location.href = R.orders + '?q=' + encodeURIComponent(q);
                else UI.toast(`Nada encontrado para "${q}".`, { type: 'info', title: 'Busca' });
            });
            L.searchInput = search;
        }
    }

    /** Executa quando o HTML estiver pronto (e a página não tiver sido redirecionada). */
    GlowUp.ready = function (fn) {
        if (GlowUp.blocked) return;
        if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', fn); else fn();
    };
    GlowUp.ready(wire);
})(window);

/* Foto que não carrega vira a imagem padrão do GlowUp (no lugar de links externos). */
document.addEventListener('error', function (e) {
    const t = e.target;
    if (t && t.tagName === 'IMG' && !t.dataset.fallback && t.getAttribute('src') !== 'assets/img/logo-flor.svg') {
        t.dataset.fallback = '1';
        t.src = GlowUp.utils.placeholderImage;
    }
}, true);
