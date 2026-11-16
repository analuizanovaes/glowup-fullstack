/* Tela: vitrini.html (catálogo da cliente) */
GlowUp.ready(async function () {
    const { utils: U, store: S, ui: UI, layout: L } = GlowUp;
    const esc = U.escapeHtml;

    const grid = U.$('.products-grid');
    const countEl = U.$('.filter-toolbar > span');
    const brandBar = U.$('.categories-filter');
    const catMenu = U.$('#dropdown-category .dropdown-menu');
    const state = { brand: 'Todas', category: 'Todos', sort: 'Destaques', q: U.query('q') || '' };
    let all = [];

    /* ---- filtros ---- */
    function filtered() {
        const q = U.normalize(state.q);
        let list = all.filter(p => {
            if (state.brand !== 'Todas' && p.marca !== state.brand) return false;
            if (state.category === 'Masculino' || state.category === 'Feminino') {
                const re = state.category === 'Masculino' ? /masculin/i : /feminin/i;
                if (!re.test(p.nome + ' ' + (p.descricao || '') + ' ' + (p.categoria || ''))) return false;
            } else if (state.category !== 'Todos' && p.categoria !== state.category) return false;
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
        void (async () => {
        const id = Number(btn.closest('.product-card').dataset.id);
        if (!Number.isFinite(id) || id <= 0) { UI.toast('Aguarde o catálogo terminar de carregar.', { type: 'info', title: 'Carregando' }); return; }
        btn.disabled = true;
        const res = await S.cartAdd(id, 1);
        btn.disabled = false;
        if (!res.ok) {
            const pending = /aguardando pagamento/i.test(res.message || '');
            UI.toast(res.message, Object.assign({ type: 'warning', title: 'Não foi possível adicionar' },
                pending ? { actions: [{ label: 'Ver pedido', onClick: () => { location.href = GlowUp.config.routes.myOrders; } }] } : {}));
            return;
        }
        L.refreshCart();
        btn.classList.add('gu-added');
        setTimeout(() => btn.classList.remove('gu-added'), 500);
        UI.toast(`"${(res.product && res.product.nome) || 'Produto'}" foi para a sua sacola.`, {
            type: 'success', title: 'Adicionado', duration: 3500,
            actions: [{ label: 'Ver sacola', onClick: () => { location.href = GlowUp.config.routes.cart; } }]
        });
        })();
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
        const kitComps = S.isManualKit(p) ? S.kitComponents(p) : []; // composição do kit manual

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
            
            ${kitComps.length ? `<div style="font-size: 0.9rem; color: var(--text-dark); margin-bottom: 1rem;">
                <strong>Este kit contém:</strong>
                <ul style="margin: 0.4rem 0 0 1.1rem; padding: 0; line-height: 1.6;">
                    ${kitComps.map(c => `<li>${c.qtd}× ${esc(c.produto.nome)}</li>`).join('')}
                </ul>
            </div>` : ''}

            <div style="font-size: 0.8rem; color: var(--text-muted); background: var(--badge-bg); padding: 0.5rem 1rem; border-radius: 50px; display: inline-block;">
                ${p.tipoVenda === 'kit' ? 'Kit · ' : ''}${pronta ? 'Pronta-Entrega' : 'Sob Encomenda'} ${soldOut ? '· Esgotado' : ''}
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
                    onClick: async () => {
                        if (soldOut) return false;
                        const res = await S.cartAdd(p.id, 1);
                        if (res.ok) {
                            L.refreshCart();
                            UI.toast('Produto adicionado à sacola!', { type: 'success' });
                        } else {
                            UI.toast(res.message, { type: 'warning' });
                        }
                        return false;
                    }
                }
            ]
        });
    });

    /* ---- marcas e categorias (geradas a partir do catálogo real) ---- */
    function buildFilters() {
        const marcas = [...new Set(all.map(p => p.marca).filter(Boolean))].sort((a, b) => a.localeCompare(b, 'pt-BR'));
        brandBar.innerHTML = ['Todas', ...marcas].map(m =>
            `<button class="cat-btn${m === 'Todas' ? ' active' : ''}" data-brand="${esc(m)}">${esc(m)}</button>`).join('');
        U.$$('.cat-btn', brandBar).forEach(b => b.addEventListener('click', () => {
            U.$$('.cat-btn', brandBar).forEach(x => x.classList.remove('active'));
            b.classList.add('active');
            state.brand = b.dataset.brand;
            render();
        }));

        const opcoes = ['Todos', 'Feminino', 'Masculino'];
        catMenu.innerHTML = opcoes.map(c =>
            `<div class="dropdown-item${c === 'Todos' ? ' selected' : ''}" data-value="${esc(c)}">${esc(c)}</div>`).join('');
        const catDrop = catMenu.closest('.custom-dropdown');
        U.$$('.dropdown-item', catMenu).forEach(item => item.addEventListener('click', () => {
            U.$$('.dropdown-item', catMenu).forEach(i => i.classList.remove('selected'));
            item.classList.add('selected');
            catDrop.querySelector('.selected-value').textContent = item.textContent;
            catDrop.classList.remove('open');
            state.category = item.dataset.value;
            render();
        }));
    }

    /* ---- dropdowns (categoria / ordenação) ---- */
    U.$$('.custom-dropdown').forEach(dropdown => {
        const btn = U.$('.dropdown-select-btn', dropdown);
        const items = dropdown.id === 'dropdown-category' ? [] : U.$$('.dropdown-item', dropdown);
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

    const isCold = e => !e || e.status == null || [408, 425, 502, 503, 504].includes(e.status) || e.name === 'AbortError' || e.name === 'TypeError';
    const status = (title, small, retry) => {
        countEl.textContent = '';
        grid.innerHTML = `<div class="gu-empty">${title}<small>${small}</small>${retry ? '<br><button type="button" class="btn-add-cart" id="catalog-retry" style="width:auto;padding:.5rem 1.2rem;margin-top:.75rem">Tentar novamente</button>' : ''}</div>`;
        const b = U.$('#catalog-retry'); if (b) b.addEventListener('click', loadAll);
    };

    async function loadAll() {
        status('Carregando catálogo…', 'Se demorar um pouco, o servidor está acordando.');
        GlowUp.showPage();
        let lastError = null;
        for (let attempt = 1; attempt <= 4; attempt++) {
            try {
                const [prods, kits] = await Promise.all([S.listProducts(), S.listKits().catch(() => [])]);
                const seen = new Set(prods.map(p => p.id));
                all = prods.concat((kits || []).filter(k => !seen.has(k.id)));
                lastError = null;
                break;
            } catch (e) {
                lastError = e;
                if (e && e.status === 401) { location.replace(GlowUp.config.routes.login); return; }
                if (!isCold(e) || attempt === 4) break;
                status('Acordando o servidor…', `Tentativa ${attempt + 1} de 4.`);
                await new Promise(r => setTimeout(r, 12000));
            }
        }
        if (lastError) { status('Não foi possível carregar o catálogo', GlowUp.api.errorMessage(lastError), true); return; }
        buildFilters();
        render();
    }
    await loadAll();
});