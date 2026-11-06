/* Tela: vitrini.html (catálogo da cliente) */
GlowUp.ready(function () {
    const { utils: U, store: S, ui: UI, layout: L } = GlowUp;
    const esc = U.escapeHtml;

    const grid = U.$('.products-grid');
    const countEl = U.$('.filter-toolbar > span');
    const brandButtons = U.$$('.cat-btn');
    const state = { brand: 'Todas', category: 'Todas', sort: 'Destaques', q: U.query('q') || '' };

    /* ---- filtros ---- */
    function filtered() {
        const q = U.normalize(state.q);
        let list = S.listProducts().filter(p => {
            if (state.brand !== 'Todas' && p.marca !== state.brand) return false;
            if (state.category !== 'Todas' && p.categoria !== state.category) return false;
            if (q && !U.normalize(p.nome + ' ' + p.marca + ' ' + p.categoria).includes(q)) return false;
            return true;
        });
        if (state.sort === 'Menor preço') list.sort((a, b) => S.effectivePrice(a) - S.effectivePrice(b));
        else if (state.sort === 'Maior preço') list.sort((a, b) => S.effectivePrice(b) - S.effectivePrice(a));
        else list.sort((a, b) => a.id - b.id);
        return list;
    }

    function cardHtml(p) {
        const pronta = p.modalidade === 'PRONTA_ENTREGA';
        const av = S.available(p);
        const soldOut = pronta && av === 0;
        const sale = S.isOnSale(p);
        const price = sale
            ? `<span class="product-price"><span class="product-price-old">${U.money(p.precoBase)}</span>${U.money(S.effectivePrice(p))}</span>`
            : `<span class="product-price">${U.money(p.precoBase)}</span>`;
        return `<div class="product-card${soldOut ? ' is-soldout' : ''}" data-id="${p.id}">
            <div class="product-image-container">
                <img src="${esc(p.imagem)}" alt="${esc(p.nome)}" loading="lazy">
                <span class="tag-status ${pronta ? 'tag-pronta-entrega' : 'tag-sob-encomenda'}">${pronta ? 'Pronta-Entrega' : 'Sob Encomenda'}</span>
                ${soldOut ? '<span class="tag-soldout">Esgotado</span>' : ''}
            </div>
            <div class="product-info">
                <div>
                    <span class="brand-name">${esc(p.marca.toUpperCase())}</span>
                    <h2 class="product-title">${esc(p.nome)}</h2>
                    ${p.descricao ? `<p class="product-desc">${esc(p.descricao)}</p>` : ''}
                </div>
                <div class="product-footer">
                    ${price}
                    <button class="btn-add-cart" aria-label="Adicionar ${esc(p.nome)} ao carrinho" ${soldOut ? 'disabled' : ''}>+</button>
                </div>
            </div>
        </div>`;
    }

    function render() {
        const list = filtered();
        countEl.textContent = list.length === 1 ? '1 produto' : `${list.length} produtos`;
        grid.innerHTML = list.length
            ? list.map(cardHtml).join('')
            : '<div class="gu-empty">Nenhum produto encontrado<small>Tente outra busca, marca ou categoria.</small></div>';
    }

    /* ---- adicionar à sacola ---- */
    grid.addEventListener('click', function (e) {
        const btn = e.target.closest('.btn-add-cart');
        if (!btn) return;
        const id = Number(btn.closest('.product-card').dataset.id);
        const res = S.cartAdd(id, 1);
        if (!res.ok) { UI.toast(res.message, { type: 'warning', title: 'Não foi possível adicionar' }); return; }
        L.refreshCart();
        btn.classList.add('gu-added');
        setTimeout(() => btn.classList.remove('gu-added'), 500);
        UI.toast(`"${res.product.nome}" foi para a sua sacola.`, {
            type: 'success', title: 'Adicionado', duration: 3500,
            actions: [{ label: 'Ver sacola', onClick: () => { location.href = GlowUp.config.routes.cart; } }]
        });
    });

    /* ---- abrir detalhes do produto (modal) ---- */
    grid.addEventListener('click', function (e) {
        // Se clicou no botão de adicionar, ignora para não abrir o modal junto
        if (e.target.closest('.btn-add-cart')) return; 
        
        const card = e.target.closest('.product-card');
        if (!card) return;
        
        const id = Number(card.dataset.id);
        const p = S.getProduct(id);
        if (!p) return;

        const pronta = p.modalidade === 'PRONTA_ENTREGA';
        const av = S.available(p);
        const soldOut = pronta && av === 0;

        // Monta o HTML interno do modal
        const node = document.createElement('div');
        node.innerHTML = `
            <div style="text-align: center; margin-bottom: 1rem;">
                <img src="${esc(p.imagem)}" alt="${esc(p.nome)}" style="width: 100%; max-height: 250px; object-fit: cover; border-radius: 1rem;">
            </div>
            <span class="brand-name" style="color: var(--primary-pink); font-weight: bold; font-size: 0.8rem;">${esc(p.marca.toUpperCase())}</span>
            <h2 style="font-family: 'Playfair Display', serif; font-size: 1.5rem; margin: 0.5rem 0;">${esc(p.nome)}</h2>
            <p style="font-size: 1.2rem; font-weight: bold; color: var(--primary-pink); margin-bottom: 1rem;">${U.money(S.effectivePrice(p))}</p>
            
            <div style="font-size: 0.95rem; line-height: 1.6; color: var(--text-muted); margin-bottom: 1rem;">
                ${esc(p.descricao || 'Nenhuma descrição cadastrada para este produto.')}
            </div>
            
            <div style="font-size: 0.8rem; color: var(--text-muted); background: var(--badge-bg); padding: 0.5rem 1rem; border-radius: 50px; display: inline-block;">
                ${pronta ? 'Pronta-Entrega' : 'Sob Encomenda'} ${soldOut ? '· Esgotado' : ''}
            </div>
        `;

        // Abre o modal usando o sistema de UI do seu projeto
        UI.modal({
            title: 'Detalhes do Produto',
            node: node,
            actions: [
                { label: 'Fechar', value: false },
                { 
                    label: soldOut ? 'Esgotado' : 'Adicionar à sacola', 
                    primary: !soldOut,
                    value: true,
                    onClick: () => {
                        if (soldOut) return false;
                        const res = S.cartAdd(p.id, 1);
                        if (res.ok) {
                            L.refreshCart();
                            UI.toast('Produto adicionado à sacola!', { type: 'success' });
                        } else {
                            UI.toast(res.message, { type: 'warning' });
                        }
                    }
                }
            ]
        });
    });

    /* ---- marcas ---- */
    brandButtons.forEach(b => b.addEventListener('click', () => {
        brandButtons.forEach(x => x.classList.remove('active'));
        b.classList.add('active');
        state.brand = b.textContent.trim();
        render();
    }));

    /* ---- dropdowns (categoria / ordenação) ---- */
    U.$$('.custom-dropdown').forEach(dropdown => {
        const btn = U.$('.dropdown-select-btn', dropdown);
        const items = U.$$('.dropdown-item', dropdown);
        const label = U.$('.selected-value', dropdown);
        btn.addEventListener('click', e => {
            e.stopPropagation();
            U.$$('.custom-dropdown').forEach(d => { if (d !== dropdown) d.classList.remove('open'); });
            dropdown.classList.toggle('open');
        });
        items.forEach(item => item.addEventListener('click', () => {
            items.forEach(i => i.classList.remove('selected'));
            item.classList.add('selected');
            label.textContent = item.textContent;
            dropdown.classList.remove('open');
            state[dropdown.id === 'dropdown-category' ? 'category' : 'sort'] = item.dataset.value;
            render();
        }));
    });
    window.addEventListener('click', () => U.$$('.custom-dropdown').forEach(d => d.classList.remove('open')));

    /* ---- busca do topo ---- */
    L.onSearch = (q) => { state.q = q; render(); };

    // avisa se algo da sacola mudou de estoque (RF06)
    S.cartReconcile().forEach(m => UI.toast(m, { type: 'warning', title: 'Sua sacola mudou' }));
    L.refreshCart();
    render();
});