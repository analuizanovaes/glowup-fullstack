/* Tela: visaogeral.html (painel da administradora). Todos os números vêm da API; nada é fixo no HTML. */
GlowUp.ready(async function () {
    const { utils: U, store: S, ui: UI, config: C } = GlowUp;
    const R = C.routes;
    const esc = U.escapeHtml;
    const me = S.currentUser();

    const hour = new Date().getHours();
    const hello = hour < 12 ? 'Bom dia' : hour < 18 ? 'Boa tarde' : 'Boa noite';
    U.$('.page-title').textContent = `${hello}, ${U.firstName(me.nome)}`;
    U.$('.page-subtitle').textContent = U.formatDateLong(new Date());

    const filterBtn = U.$('#filterBtn');
    const filterMenu = U.$('#filterMenu');
    const label = U.$('#currentFilterLabel');
    const items = U.$$('.filter-item');
    let period = (U.$('.filter-item.active') || items[0]).dataset.value;
    let seq = 0; // só a resposta da última consulta pode pintar a tela (evita "pisca e volta")

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

    const cards = U.$$('.stat-card');
    const boxes = U.$$('.panel-box');
    const recentBox = boxes[0];
    const chartBox = U.$('.chart-container');
    const trend = U.$('#trendBadge');

    const errorHost = document.createElement('div');
    errorHost.style.marginBottom = '1.5rem';
    U.$('.stats-grid').before(errorHost);
    errorHost.addEventListener('click', e => { if (e.target.closest('[data-retry]')) render(); });

    function setCard(i, value, extra, color) {
        U.$('.stat-value', cards[i]).textContent = value;
        const ex = U.$('.stat-extra', cards[i]);
        ex.textContent = extra || '';
        ex.style.color = color || 'var(--text-muted)';
    }

    function showError(e) {
        errorHost.innerHTML = UI.errorBox(e && e.message, 'Os números do painel não foram carregados.');
        [0, 1, 2, 3].forEach(i => setCard(i, '—', ''));
        chartBox.innerHTML = '';
        trend.textContent = '—'; trend.style.color = 'var(--text-muted)';
        U.$$('.recent-order-item, .gu-empty', recentBox).forEach(x => x.remove());
    }

    function paint(m) {
        errorHost.innerHTML = '';
        const deltaTxt = m.delta === null ? 'Sem período anterior para comparar' : `${m.delta >= 0 ? '+' : ''}${String(m.delta).replace('.', ',')}% no período`;
        setCard(0, m.vendas, deltaTxt, m.delta === null ? null : m.delta >= 0 ? '#2e7d32' : '#d32f2f');
        setCard(1, U.money(m.faturamento), `Ticket médio ${U.money(m.ticket)}`);
        setCard(2, U.money(m.lucro), m.semDados ? `${m.semDados} pedido${m.semDados === 1 ? '' : 's'} sem dados de custo/taxa` : 'Após custos e taxas');
        setCard(3, m.estoqueTotal, m.estoqueBaixo ? `${m.estoqueBaixo} com estoque baixo` : 'Estoque saudável', m.estoqueBaixo ? '#d32f2f' : '#2e7d32');

        const max = Math.max.apply(null, m.chart.map(b => b.fat)) || 1;
        chartBox.innerHTML = m.chart.map(b => {
            const fat = Math.max(4, Math.round(b.fat / max * 100));
            const luc = Math.max(4, Math.round(Math.max(b.lucro, 0) / max * 100));
            return `<div class="chart-bar-group"><div class="chart-bar light" style="height:${b.fat ? luc : 4}%" title="${esc(b.label)} · Lucro real: ${U.money(b.lucro)}"></div></div>` +
                   `<div class="chart-bar-group"><div class="chart-bar" style="height:${b.fat ? fat : 4}%" title="${esc(b.label)} · Faturamento: ${U.money(b.fat)}"></div></div>`;
        }).join('');
        trend.textContent = m.delta === null ? '—' : `${m.delta >= 0 ? '+' : ''}${String(m.delta).replace('.', ',')}%`;
        trend.style.color = m.delta === null ? 'var(--text-muted)' : m.delta < 0 ? '#d32f2f' : '#2e7d32';

        U.$$('.recent-order-item, .gu-empty', recentBox).forEach(x => x.remove());
        recentBox.insertAdjacentHTML('beforeend', m.recentes.map(o => `
            <div class="recent-order-item" data-id="${o.id}" style="cursor:pointer">
                <div><div class="order-id-name">#${o.id}</div><div class="order-date-client">${esc(o.clienteNome)} · ${U.formatDate(o.criadoEm)}</div></div>
                <span class="status-badge status-${S.statusClass(o.status)}">${esc(S.statusLabel(o.status))}</span>
                <div style="font-weight: 700;">${U.money(o.total)}</div>
            </div>`).join('') || '<div class="gu-empty">Nenhum pedido ainda</div>');
    }

    async function render() {
        const mine = ++seq;
        try {
            const m = await S.dashboardMetrics(period);
            if (mine === seq) paint(m);
        } catch (e) {
            console.error(e);
            if (mine === seq) showError(e);
        }
    }

    async function renderAlerts() {
        const card = U.$('.alert-card');
        try {
            const alerts = await S.expiryAlerts();
            card.style.display = alerts.length ? '' : 'none';
            U.$('.alert-items-grid', card).innerHTML = alerts.map(a => `
                <div class="alert-product-box">
                    <div class="alert-product-info">
                        <img src="${esc(a.produto.imagem)}" class="alert-product-img" alt="${esc(a.produto.nome)}">
                        <div>
                            <div class="app-product-title" style="font-size: 0.85rem; font-weight: 700; color: var(--text-dark);">${esc(a.produto.nome)}</div>
                            <div class="alert-product-sub">${a.produto.estoque == null ? 'Estoque não informado' : `${a.produto.estoque} unidade${a.produto.estoque === 1 ? '' : 's'} em estoque`}</div>
                        </div>
                    </div>
                    <span class="badge-days">${a.dias <= 0 ? 'Vencido' : a.dias + ' dia' + (a.dias === 1 ? '' : 's')}</span>
                </div>`).join('');
        } catch (e) {
            console.error(e);
            card.style.display = 'none';
            UI.toast('Não foi possível carregar os alertas de validade.', { type: 'warning', title: 'Alertas' });
        }
    }

    document.addEventListener('click', function (e) {
        const row = e.target.closest('.recent-order-item');
        if (row) location.href = R.orders + '?q=' + row.dataset.id;
    });

    await Promise.all([render(), renderAlerts()]);
});
