/* Tela: carrinho.html */
GlowUp.ready(function () {
    const { utils: U, store: S, ui: UI, layout: L, config: C } = GlowUp;
    const R = C.routes;
    const esc = U.escapeHtml;

    const content = U.$('.cart-main-content');
    const topBar = U.$('.top-action-bar');
    const empty = U.$('.empty-cart-container');
    const clearBtn = U.$('.btn-clear-cart');
    const rows = U.$$('.summary-card .summary-row');
    const subtotalEl = rows[0].lastElementChild;
    const totalRow = U.$('.total-row');
    const totalEl = totalRow.lastElementChild;
    const couponInput = U.$('.coupon-input');
    const couponBtn = U.$('.btn-apply');
    const checkoutBtn = U.$('.btn-checkout');

    U.$('.btn-explore').setAttribute('href', R.store);
    checkoutBtn.setAttribute('href', R.checkout);

    const list = document.createElement('div');
    list.className = 'cart-items';
    content.insertBefore(list, empty);

    let discountRow = null;

    function lineHtml(l) {
        const p = l.produto;
        const warn = l.issue === 'reduced' ? `<span class="cart-warn">Só há ${l.disponivel} em estoque</span>` :
            l.issue === 'soldout' ? '<span class="cart-warn">Esgotado</span>' : '';
        const max = l.disponivel;
        return `<article class="cart-item" data-id="${p.id}">
            <img class="cart-item-img" src="${esc(p.imagem)}" alt="${esc(p.nome)}">
            <div class="cart-item-info">
                <span class="cart-item-brand">${esc(p.marca.toUpperCase())}</span>
                <h3 class="cart-item-title">${esc(p.nome)}</h3>
                <span class="cart-item-unit">${U.money(l.unit)} cada${p.modalidade === 'SOB_ENCOMENDA' ? ' • Sob encomenda' : ''}</span>
                ${warn}
            </div>
            <div class="cart-qty" role="group" aria-label="Quantidade">
                <button type="button" class="cart-qty-btn" data-act="dec" aria-label="Diminuir">&minus;</button>
                <span class="cart-qty-value">${l.qtd}</span>
                <button type="button" class="cart-qty-btn" data-act="inc" aria-label="Aumentar" ${max !== null && l.qtd >= max ? 'disabled' : ''}>+</button>
            </div>
            <strong class="cart-item-total">${U.money(l.subtotal)}</strong>
            <button type="button" class="cart-item-remove" data-act="remove" aria-label="Remover ${esc(p.nome)}">${UI.icons.trash}</button>
        </article>`;
    }

    function render() {
        const t = S.cartTotals();
        const has = t.lines.length > 0;
        list.innerHTML = t.lines.map(lineHtml).join('');
        empty.style.display = has ? 'none' : '';
        topBar.style.display = has ? '' : 'none';
        subtotalEl.textContent = U.money(t.subtotal);
        totalEl.textContent = U.money(t.total);

        if (discountRow) { discountRow.remove(); discountRow = null; }
        if (t.cupom) {
            discountRow = document.createElement('div');
            discountRow.className = 'summary-row';
            discountRow.innerHTML = `<span>Cupom ${esc(t.cupom)} <button type="button" class="cart-coupon-remove">remover</button></span><span class="cart-discount">- ${U.money(t.desconto)}</span>`;
            totalRow.parentNode.insertBefore(discountRow, U.$('.coupon-container'));
            couponInput.value = t.cupom; couponInput.disabled = true; couponBtn.disabled = true;
        } else {
            couponInput.disabled = false; couponBtn.disabled = false;
        }
        checkoutBtn.classList.toggle('is-disabled', !has);
        L.refreshCart();
    }

    list.addEventListener('click', async function (e) {
        const btn = e.target.closest('[data-act]');
        if (!btn) return;
        const id = Number(btn.closest('.cart-item').dataset.id);
        const line = S.cartLines().find(l => l.produtoId === id);
        const act = btn.dataset.act;
        if (act === 'remove') {
            const ok = await UI.confirm({ title: 'Remover item?', message: `"${line.produto.nome}" sairá da sua sacola.`, confirmText: 'Remover', danger: true });
            if (!ok) return;
            S.cartRemove(id); render();
            UI.toast('Item removido da sacola.', { type: 'info', title: 'Sacola' });
            return;
        }
        const res = S.cartSetQty(id, line.qtd + (act === 'inc' ? 1 : -1));
        if (!res.ok && res.message) UI.toast(res.message, { type: 'warning', title: 'Estoque' });
        render();
    });

    clearBtn.addEventListener('click', async function () {
        const ok = await UI.confirm({ title: 'Esvaziar carrinho?', message: 'Todos os itens serão removidos da sua sacola.', confirmText: 'Esvaziar', danger: true });
        if (!ok) return;
        S.cartClear(); render();
        UI.toast('Sua sacola está vazia.', { type: 'info', title: 'Carrinho esvaziado' });
    });

    function applyCoupon() {
        if (couponInput.disabled) return;
        const res = S.applyCoupon(couponInput.value);
        if (!res.ok) { UI.toast(res.message, { type: 'error', title: 'Cupom' }); couponInput.classList.add('has-error'); return; }
        couponInput.classList.remove('has-error');
        render();
        UI.toast(`Cupom ${res.cupom.codigo} aplicado: você economizou ${U.money(res.desconto)}.`, { type: 'success', title: 'Cupom aplicado' });
    }
    couponBtn.addEventListener('click', applyCoupon);
    couponInput.addEventListener('keydown', e => { if (e.key === 'Enter') applyCoupon(); });
    document.addEventListener('click', function (e) {
        if (!e.target.classList.contains('cart-coupon-remove')) return;
        S.removeCoupon(); couponInput.value = ''; render();
        UI.toast('Cupom removido.', { type: 'info' });
    });

    checkoutBtn.addEventListener('click', function (e) {
        const t = S.cartTotals();
        if (!t.lines.length) { e.preventDefault(); UI.toast('Adicione produtos à sacola para continuar.', { type: 'warning', title: 'Sacola vazia' }); return; }
        const bad = t.lines.find(l => l.issue);
        if (bad) { e.preventDefault(); UI.toast(`Ajuste "${bad.produto.nome}": o estoque mudou.`, { type: 'warning', title: 'Estoque' }); }
    });

    S.cartReconcile().forEach(m => UI.toast(m, { type: 'warning', title: 'Sua sacola mudou' }));
    render();
});
