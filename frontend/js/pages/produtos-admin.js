/* Tela: produtos.html (catálogo da administradora). Dados de GET /api/admin/produtos. */
GlowUp.ready(async function () {
    const { utils: U, store: S, ui: UI, layout: L, config: C } = GlowUp;
    const R = C.routes;
    const esc = U.escapeHtml;
    const grid = U.$('.products-grid-admin');
    let query = U.query('q') || '';
    let seq = 0;

    const ICON_EDIT = '<svg width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path d="M11 4H4a2 2 0 00-2 2v14a2 2 0 002 2h14a2 2 0 002-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 013 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>';
    const ICON_TRASH = '<svg width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2"/></svg>';

    U.$('.btn-primary').addEventListener('click', () => { location.href = R.productForm; });
    U.$('.btn-secondary').addEventListener('click', () => { location.href = R.productForm + '?tipo=kit'; });
    U.$('#btn-inactive').addEventListener('click', () => { location.href = R.inactiveProducts; });

    function card(p) {
        const pronta = p.modalidade === 'PRONTA_ENTREGA';
        let sub = pronta ? `Pronta-Entrega · ${p.estoque ?? 0} un.` : 'Sob Encomenda';
        if (S.isManualKit(p)) {
            const n = (p.itensKit || []).length;
            sub = `Kit manual · ${n} ${n === 1 ? 'item' : 'itens'} · ${S.available(p) ?? 0} kit(s) possíveis`;
        } else if (p.tipoVenda === 'kit') {
            sub = 'Kit pronto · ' + sub;
        }
        const sale = S.isOnSale(p) && p.dataFimPromocao ? ` · Promoção até ${U.formatDateBR(p.dataFimPromocao + 'T12:00:00')}` : '';
        return `<div class="admin-product-card" data-id="${p.id}">
            <img src="${esc(p.imagem)}" class="admin-product-img" alt="${esc(p.nome)}">
            <div class="admin-product-body">
                <div>
                    <span class="admin-product-brand">${esc(p.marca)}</span>
                    <div class="admin-product-title" title="${esc(p.nome)}">${esc(p.nome)}</div>
                    <div class="admin-product-sub">${sub}${sale}</div>
                </div>
                <div class="admin-product-footer">
                    <span class="admin-product-price">${U.money(S.effectivePrice(p))}</span>
                    <div class="admin-product-actions">
                        <button type="button" class="icon-action-btn" data-act="edit" aria-label="Editar ${esc(p.nome)}">${ICON_EDIT}</button>
                        <button type="button" class="icon-action-btn" data-act="remove" aria-label="Inativar ${esc(p.nome)}">${ICON_TRASH}</button>
                    </div>
                </div>
            </div>
        </div>`;
    }

    function paint(all) {
        const n = U.normalize(query);
        // produtos inativados (exclusão lógica) não aparecem; os mais novos primeiro
        const list = all.filter(p => p.ativo !== false)
            .filter(p => !n || U.normalize(p.nome + ' ' + p.marca + ' ' + p.categoria).includes(n))
            .sort((a, b) => b.id - a.id);
        grid.innerHTML = list.length ? list.map(card).join('')
            : `<div class="gu-empty">${all.length ? 'Nenhum produto encontrado' : 'Nenhum produto cadastrado'}<small>${all.length ? 'Ajuste a busca.' : 'Cadastre o primeiro produto ou kit.'}</small></div>`;
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
        const btn = e.target.closest('[data-act]');
        if (!btn) return;
        const p = S.getProduct(btn.closest('.admin-product-card').dataset.id);
        if (!p) return;
        if (btn.dataset.act === 'edit') { location.href = R.productForm + '?id=' + p.id; return; }
        // item que compõe kit manual não pode sair do catálogo sem antes sair do kit
        const kits = S.kitsUsing(p.id);
        if (kits.length) {
            UI.toast(`"${p.nome}" faz parte do kit manual "${kits[0].nome}"${kits.length > 1 ? ' e de outros' : ''}. Tire-o do kit (ou inative o kit) antes.`, { type: 'warning', title: 'Não foi possível inativar' });
            return;
        }
        const ok = await UI.confirm({
            title: 'Inativar produto?',
            message: `"${p.nome}" sai da vitrine, mas o histórico de vendas é preservado (exclusão lógica).`,
            confirmText: 'Inativar', danger: true
        });
        if (!ok) return;
        const res = await S.removeProduct(p.id);
        if (!res.ok) { UI.toast(res.message || 'O produto não foi inativado.', { type: 'error', title: 'Não foi possível inativar' }); return; }
        UI.toast('O produto foi inativado e não aparece mais para as clientes.', { type: 'success', title: 'Produto inativado' });
        await load(false);
    });

    L.onSearch = (q) => { query = q; load(false); };
    await load(true);
});
