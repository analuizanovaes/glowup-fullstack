/* Tela: produtosinativos.html (produtos inativados pela administradora). Dados de GET /api/admin/produtos; reativa por PATCH /api/admin/produtos/{id}/reativar. */
GlowUp.ready(async function () {
    const { utils: U, store: S, ui: UI, layout: L, config: C } = GlowUp;
    const R = C.routes;
    const esc = U.escapeHtml;
    const grid = U.$('.products-grid-admin');
    let query = U.query('q') || '';
    let seq = 0;

    const ICON_RESTORE = '<svg width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path d="M1 4v6h6"/><path d="M3.51 15a9 9 0 102.13-9.36L1 10"/></svg>';

    U.$('#btn-back').addEventListener('click', () => { location.href = R.products; });

    function card(p) {
        const pronta = p.modalidade === 'PRONTA_ENTREGA';
        let sub = pronta ? `Pronta-Entrega · ${p.estoque ?? 0} un.` : 'Sob Encomenda';
        if (p.tipoVenda === 'kit') sub = (S.isManualKit(p) ? 'Kit manual' : 'Kit pronto') + ' · ' + sub;
        return `<div class="admin-product-card is-inactive" data-id="${p.id}">
            <img src="${esc(p.imagem)}" class="admin-product-img" alt="${esc(p.nome)}">
            <div class="admin-product-body">
                <div>
                    <span class="inactive-badge">Inativo</span>
                    <span class="admin-product-brand">${esc(p.marca)}</span>
                    <div class="admin-product-title" title="${esc(p.nome)}">${esc(p.nome)}</div>
                    <div class="admin-product-sub">${sub}</div>
                </div>
                <div class="admin-product-footer">
                    <span class="admin-product-price">${U.money(p.precoBase)}</span>
                    <div class="admin-product-actions">
                        <button type="button" class="btn-reactivate" data-act="restore" aria-label="Reativar ${esc(p.nome)}">${ICON_RESTORE}Reativar</button>
                    </div>
                </div>
            </div>
        </div>`;
    }

    function paint(all) {
        const n = U.normalize(query);
        const inactive = all.filter(p => p.ativo === false);
        const list = inactive
            .filter(p => !n || U.normalize(p.nome + ' ' + p.marca + ' ' + p.categoria).includes(n))
            .sort((a, b) => b.id - a.id);
        grid.innerHTML = list.length ? list.map(card).join('')
            : `<div class="gu-empty">${inactive.length ? 'Nenhum produto encontrado' : 'Nenhum produto inativo'}<small>${inactive.length ? 'Ajuste a busca.' : 'Quando você inativar um produto, ele aparece aqui para ser reativado quando quiser.'}</small></div>`;
    }

    async function load(force) {
        const mine = ++seq;
        try {
            const all = force ? await S.reloadCatalog() : await S.listProducts();
            if (mine === seq) paint(all);
        } catch (e) {
            console.error(e);
            if (mine === seq) grid.innerHTML = UI.errorBox(e.message, 'O catálogo não foi carregado.');
        }
    }

    grid.addEventListener('click', async function (e) {
        if (e.target.closest('[data-retry]')) { load(true); return; }
        const btn = e.target.closest('[data-act="restore"]');
        if (!btn) return;
        const p = S.getProduct(btn.closest('.admin-product-card').dataset.id);
        if (!p) return;
        const ok = await UI.confirm({
            title: 'Reativar produto?',
            message: `"${p.nome}" volta para a vitrine com o mesmo preço, estoque e histórico.`,
            confirmText: 'Reativar'
        });
        if (!ok) return;
        UI.setLoading(btn, true, 'Reativando...');
        const res = await S.restoreProduct(p.id);
        if (!res.ok) {
            UI.setLoading(btn, false);
            UI.toast(res.message || 'O produto não foi reativado.', { type: 'error', title: 'Não foi possível reativar' });
            return;
        }
        UI.toast('O produto voltou para a vitrine e já aparece para as clientes.', { type: 'success', title: 'Produto reativado' });
        await load(false);
    });

    L.onSearch = (q) => { query = q; load(false); };
    await load(true);
});
