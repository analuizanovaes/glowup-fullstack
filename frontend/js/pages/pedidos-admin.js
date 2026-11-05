/* Tela: pedidos.html (gestão de pedidos da administradora) */
GlowUp.ready(function () {
    const { utils: U, store: S, ui: UI, layout: L } = GlowUp;
    const esc = U.escapeHtml;
    const tbody = U.$('.data-table tbody');
    let query = U.query('q') || '';

    function rowHtml(o) {
        const next = S.nextStatus(o);
        const action = next
            ? `<button type="button" class="action-btn" data-act="advance">Avançar para ${next}</button>`
            : `<button type="button" class="action-btn" disabled>${o.status === 'Aguardando pagamento' ? 'Aguardando pagamento' : 'Sem ações'}</button>`;
        return `<tr data-id="${o.id}">
            <td style="font-weight: 700;"><button type="button" class="order-open" data-act="details" title="Ver detalhes e lucro real">${o.id}</button></td>
            <td>${esc(o.clienteNome)}</td>
            <td>${U.formatDate(o.criadoEm)}</td>
            <td>${o.entrega.tipo}</td>
            <td style="font-weight: 700;">${U.money(o.total)}</td>
            <td><span class="status-badge status-${S.statusClass(o.status)}">${esc(o.status)}</span></td>
            <td>${action}</td>
        </tr>`;
    }

    function render() {
        const n = U.normalize(query);
        const list = S.listOrders().filter(o => !n || U.normalize(o.id + ' ' + o.clienteNome + ' ' + o.status + ' ' + o.entrega.tipo).includes(n));
        tbody.innerHTML = list.length ? list.map(rowHtml).join('')
            : `<tr><td colspan="7"><div class="gu-empty" style="border:none">Nenhum pedido encontrado<small>Tente outro termo de busca.</small></div></td></tr>`;
    }

    function details(o) {
        const f = o.financeiro;
        const row = (a, b, cls) => `<div class="gu-detail-row ${cls || ''}"><span>${a}</span><span>${b}</span></div>`;
        const itens = o.itens.map(i => row(`${esc(i.nome)} × ${i.qtd}`, `${U.money(i.precoPraticado * i.qtd)} <small style="color:var(--text-muted);font-weight:500">(custo ${U.money(i.custoFabrica * i.qtd)})</small>`)).join('');
        const html = `<div class="gu-section-label">Itens (valores congelados na venda)</div><div class="gu-detail-list">${itens}</div>
            <div class="gu-section-label">Lucro real</div><div class="gu-detail-list">
                ${row('Valor da venda', U.money(f.valorVenda))}
                ${row('Custo de fábrica', '- ' + U.money(f.custoFabrica))}
                ${row('Taxas do gateway', '- ' + U.money(f.taxaGateway))}
                ${row('Lucro real', U.money(f.lucroReal), 'is-total is-profit')}
            </div>
            <div class="gu-section-label">Cobrança</div><div class="gu-detail-list">
                ${row('Frete', o.frete ? U.money(o.frete) : 'Retirada (grátis)')}
                ${o.desconto ? row('Cupom ' + esc(o.cupom || ''), '- ' + U.money(o.desconto)) : ''}
                ${o.juros ? row('Juros repassados à cliente', U.money(o.juros)) : ''}
                ${row('Total pago', U.money(o.total), 'is-total')}
                ${row('Pagamento', o.pagamento.metodo === 'PIX' ? 'Pix' : `Cartão em ${o.pagamento.parcelas}x`)}
                ${o.entrega.endereco ? row('Entrega', `${esc(o.entrega.endereco.rua)}, ${esc(o.entrega.endereco.numero)} - ${esc(o.entrega.endereco.bairro)}`) : row('Entrega', 'Retirada no local')}
            </div>`;
        UI.modal({ title: `Pedido ${o.id}`, text: `${o.clienteNome} • ${o.status} • ${U.formatDate(o.criadoEm)}`, html, wide: true, actions: [{ label: 'Fechar', primary: true }] });
    }

    tbody.addEventListener('click', async function (e) {
        const btn = e.target.closest('[data-act]');
        if (!btn) return;
        const o = S.getOrder(btn.closest('tr').dataset.id);
        if (btn.dataset.act === 'details') { details(o); return; }
        const next = S.nextStatus(o);
        const ok = await UI.confirm({ title: 'Atualizar pedido?', message: `O pedido ${o.id} de ${o.clienteNome} passará de "${o.status}" para "${next}".`, confirmText: 'Atualizar' });
        if (!ok) return;
        const res = S.advanceOrder(o.id);
        if (!res.ok) { UI.toast(res.message, { type: 'error' }); return; }
        UI.toast(`Pedido ${o.id} agora está "${res.status}".`, { type: 'success', title: 'Status atualizado' });
        render();
    });

    L.onSearch = (q) => { query = q; render(); };
    render();
});
