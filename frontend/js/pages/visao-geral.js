/* Tela: visaogeral.html (painel da administradora) */
GlowUp.ready(function () {
    const { utils: U, store: S, config: C } = GlowUp;
    const R = C.routes;
    const esc = U.escapeHtml;
    const me = S.currentUser();

    /* ---- saudação e data ---- */
    const hour = new Date().getHours();
    const hello = hour < 12 ? 'Bom dia' : hour < 18 ? 'Boa tarde' : 'Boa noite';
    U.$('.page-title').textContent = `${hello}, ${U.firstName(me.nome)}`;
    U.$('.page-subtitle').textContent = U.formatDateLong(new Date());

    /* ---- filtro de período ---- */
    const filterBtn = U.$('#filterBtn');
    const filterMenu = U.$('#filterMenu');
    const label = U.$('#currentFilterLabel');
    const items = U.$$('.filter-item');
    let period = 'Últimos 30 dias';

    filterBtn.addEventListener('click', e => { e.stopPropagation(); filterMenu.classList.toggle('show'); });
    window.addEventListener('click', () => filterMenu.classList.remove('show'));
    items.forEach(item => item.addEventListener('click', () => {
        items.forEach(i => i.classList.remove('active'));
        item.classList.add('active');
        period = item.dataset.value;
        label.textContent = period;
        filterMenu.classList.remove('show');
        render();
    }));

    const int = (n) => 'R$ ' + Math.round(n).toLocaleString('pt-BR');

    function render() {
        const m = S.dashboardMetrics(period);
        const cards = U.$$('.stat-card');
        const set = (i, value, extra, color) => {
            U.$('.stat-value', cards[i]).textContent = value;
            const ex = U.$('.stat-extra', cards[i]);
            ex.textContent = extra;
            if (color) ex.style.color = color;
        };
        const deltaTxt = m.delta === null ? 'Sem período anterior para comparar' : `${m.delta >= 0 ? '+' : ''}${m.delta}% no período`;
        set(0, m.vendas, deltaTxt, m.delta === null ? 'var(--text-muted)' : m.delta >= 0 ? '#2e7d32' : '#d32f2f');
        set(1, int(m.faturamento), `Ticket médio ${U.money(m.ticket)}`);
        set(2, int(m.lucro), 'Após custos e taxas');
        set(3, m.estoqueTotal, m.estoqueBaixo ? `${m.estoqueBaixo} com estoque baixo` : 'Estoque saudável', m.estoqueBaixo ? '#d32f2f' : '#2e7d32');

        // gráfico: 7 blocos de tempo (lucro claro + faturamento escuro)
        const max = Math.max.apply(null, m.chart.map(b => b.fat)) || 1;
        U.$('.chart-container').innerHTML = m.chart.map(b => {
            const fat = Math.max(4, Math.round(b.fat / max * 100));
            const luc = Math.max(4, Math.round(Math.max(b.lucro, 0) / max * 100));
            return `<div class="chart-bar-group"><div class="chart-bar light" style="height:${b.fat ? luc : 4}%" title="Lucro real: ${U.money(b.lucro)}"></div></div>` +
                   `<div class="chart-bar-group"><div class="chart-bar" style="height:${b.fat ? fat : 4}%" title="Faturamento: ${U.money(b.fat)}"></div></div>`;
        }).join('');
        const trend = U.$$('.panel-box')[1].querySelector('div > span:last-child');
        trend.textContent = m.delta === null ? '—' : `${m.delta >= 0 ? '+' : ''}${m.delta}%`;
        trend.style.color = m.delta !== null && m.delta < 0 ? '#d32f2f' : '#2e7d32';

        // pedidos recentes
        const box = U.$$('.panel-box')[0];
        U.$$('.recent-order-item', box).forEach(x => x.remove());
        box.insertAdjacentHTML('beforeend', m.recentes.map(o => `
            <div class="recent-order-item" data-id="${o.id}" style="cursor:pointer">
                <div><div class="order-id-name">${o.id}</div><div class="order-date-client">${esc(o.clienteNome)} · ${U.formatDate(o.criadoEm)}</div></div>
                <span class="status-badge status-${S.statusClass(o.status)}">${esc(o.status)}</span>
                <div style="font-weight: 700;">${U.money(o.total)}</div>
            </div>`).join('') || '<div class="gu-empty">Nenhum pedido ainda</div>');
    }

    // alertas de vencimento (pronta-entrega)
    function renderAlerts() {
        const card = U.$('.alert-card');
        const alerts = S.expiryAlerts();
        card.style.display = alerts.length ? '' : 'none';
        U.$('.alert-items-grid', card).innerHTML = alerts.map(a => `
            <div class="alert-product-box">
                <div class="alert-product-info">
                    <img src="${esc(a.produto.imagem)}" class="alert-product-img" alt="${esc(a.produto.nome)}">
                    <div>
                        <div class="app-product-title" style="font-size: 0.85rem; font-weight: 700; color: var(--text-dark);">${esc(a.produto.nome)}</div>
                        <div class="alert-product-sub">${a.produto.estoque} unidade${a.produto.estoque === 1 ? '' : 's'} em estoque</div>
                    </div>
                </div>
                <span class="badge-days">${a.dias <= 0 ? 'Vencido' : a.dias + ' dia' + (a.dias === 1 ? '' : 's')}</span>
            </div>`).join('');
    }

    document.addEventListener('click', function (e) {
        const row = e.target.closest('.recent-order-item');
        if (row) location.href = R.orders + '?q=' + row.dataset.id;
    });

    render();
    renderAlerts();
});
