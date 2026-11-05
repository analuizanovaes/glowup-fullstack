/* Tela: produtos.html (catálogo da administradora) */
GlowUp.ready(function () {
    const { utils: U, store: S, ui: UI, layout: L, config: C } = GlowUp;
    const R = C.routes;
    const esc = U.escapeHtml;
    const grid = U.$('.products-grid-admin');
    const icons = U.$$('.icon-action-btn svg', grid).slice(0, 2).map(s => s.outerHTML);
    let query = U.query('q') || '';

    U.$('.btn-primary').addEventListener('click', () => { location.href = R.productForm; });

    function card(p) {
        const pronta = p.modalidade === 'PRONTA_ENTREGA';
        const sub = pronta ? `Pronta-Entrega · ${p.estoque} un.` : 'Sob Encomenda';
        const sale = S.isOnSale(p) ? ` · Promoção até ${U.formatDateBR(p.dataFimPromocao + 'T12:00:00')}` : '';
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
                        <button type="button" class="icon-action-btn" data-act="edit" aria-label="Editar ${esc(p.nome)}">${icons[0] || ''}</button>
                        <button type="button" class="icon-action-btn" data-act="remove" aria-label="Inativar ${esc(p.nome)}">${icons[1] || ''}</button>
                    </div>
                </div>
            </div>
        </div>`;
    }

    function render() {
        const n = U.normalize(query);
        const list = S.listProducts().filter(p => !n || U.normalize(p.nome + ' ' + p.marca + ' ' + p.categoria).includes(n))
            .sort((a, b) => new Date(b.atualizadoEm) - new Date(a.atualizadoEm));
        grid.innerHTML = list.length ? list.map(card).join('')
            : '<div class="gu-empty">Nenhum produto encontrado<small>Cadastre um novo produto ou ajuste a busca.</small></div>';
    }

    grid.addEventListener('click', async function (e) {
        const btn = e.target.closest('[data-act]');
        if (!btn) return;
        const p = S.getProduct(btn.closest('.admin-product-card').dataset.id);
        if (btn.dataset.act === 'edit') { location.href = R.productForm + '?id=' + p.id; return; }
        const ok = await UI.confirm({
            title: 'Inativar produto?',
            message: `"${p.nome}" sai da vitrine, mas o histórico de vendas é preservado (exclusão lógica).`,
            confirmText: 'Inativar', danger: true
        });
        if (!ok) return;
        S.removeProduct(p.id);
        UI.toast('O produto foi inativado e não aparece mais para as clientes.', { type: 'success', title: 'Produto inativado' });
        render();
    });

    L.onSearch = (q) => { query = q; render(); };
    render();
});
