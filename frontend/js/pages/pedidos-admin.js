/* Tela: pedidos.html (gestão de pedidos da administradora). Os pedidos vêm de GET /api/admin/pedidos. */
GlowUp.ready(async function () {
    const { utils: U, store: S, ui: UI, layout: L } = GlowUp;
    const esc = U.escapeHtml;
    const tbody = U.$('.data-table tbody');
    let query = U.query('q') || '';
    let all = [];
    let seq = 0; // só a resposta da última consulta pinta a tabela

    const money = (v) => (v == null ? '—' : U.money(v));
    const entregaLabel = (o) => (o.entrega.tipo === 'home' ? 'Em casa' : 'Retirada');
    const payLabel = (o) => {
        const m = o.pagamento.metodo;
        const how = m === 'PIX' ? 'Pix' : m === 'CARTAO' ? `Cartão em ${o.pagamento.parcelas || 1}x` : '—';
        const st = { PENDENTE: 'pendente', APROVADO: 'aprovado', REJEITADO: 'rejeitado', CANCELADO: 'cancelado', ESTORNADO: 'estornado' }[o.pagamento.status];
        return st ? `${how} (${st})` : how;
    };

    function actionHtml(o) {
        const next = S.nextStatus(o);
        if (next && o.conferenciaEnderecoPendente && (next === 'EM_SEPARACAO' || next === 'ENVIADO')) {
            return '<button type="button" class="action-btn" data-act="confirm-address" title="O CEP não pôde ser validado automaticamente">Conferir endereço</button>';
        }
        if (next) return `<button type="button" class="action-btn" data-act="advance">Avançar para ${esc(S.statusLabel(next))}</button>`;
        return `<button type="button" class="action-btn" disabled>${o.status === 'AGUARDANDO_PAGAMENTO' ? esc(S.statusLabel(o.status)) : 'Sem ações'}</button>`;
    }

    function rowHtml(o) {
        return `<tr data-id="${o.id}">
            <td style="font-weight: 700;"><button type="button" class="order-open" data-act="details" title="Ver detalhes e lucro real">#${o.id}</button></td>
            <td>${esc(o.clienteNome)}</td>
            <td>${U.formatDate(o.criadoEm)}</td>
            <td>${entregaLabel(o)}</td>
            <td style="font-weight: 700;">${U.money(o.total)}</td>
            <td><span class="status-badge status-${S.statusClass(o.status)}">${esc(S.statusLabel(o.status))}</span></td>
            <td>${actionHtml(o)}</td>
        </tr>`;
    }

    function paint() {
        const n = U.normalize(query);
        const list = all.filter(o => !n || U.normalize(o.id + ' ' + o.clienteNome + ' ' + S.statusLabel(o.status) + ' ' + entregaLabel(o)).includes(n));
        tbody.innerHTML = list.length ? list.map(rowHtml).join('')
            : `<tr><td colspan="7"><div class="gu-empty" style="border:none">${all.length ? 'Nenhum pedido encontrado' : 'Nenhum pedido ainda'}<small>${all.length ? 'Tente outro termo de busca.' : 'Quando uma cliente fizer um pedido, ele aparece aqui.'}</small></div></td></tr>`;
    }

    async function load(force) {
        const mine = ++seq;
        try {
            const orders = await S.listOrders(true, !!force);
            if (mine !== seq) return;
            all = orders;
            paint();
        } catch (e) {
            console.error(e);
            if (mine !== seq) return;
            all = [];
            tbody.innerHTML = `<tr><td colspan="7">${UI.errorBox(e.message, 'Os pedidos não foram carregados.')}</td></tr>`;
        }
    }

    function details(o) {
        const f = o.financeiro || {};
        const semDados = f.lucroReal == null;
        const row = (a, b, cls) => `<div class="gu-detail-row ${cls || ''}"><span>${a}</span><span>${b}</span></div>`;
        const itens = o.itens.map(i => row(`${esc(i.nome)} × ${i.qtd}`, `${U.money(i.precoPraticado * i.qtd)} <small style="color:var(--text-muted);font-weight:500">(custo ${U.money(i.custoFabrica * i.qtd)})</small>`)).join('');
        const html = `<div class="gu-section-label">Itens (valores congelados na venda)</div><div class="gu-detail-list">${itens || row('Sem itens', '—')}</div>
            <div class="gu-section-label">Lucro real</div><div class="gu-detail-list">
                ${row('Valor da venda', money(f.valorVenda ?? o.total))}
                ${row('Custo de fábrica', f.custoFabrica == null ? '—' : '- ' + U.money(f.custoFabrica))}
                ${row('Taxas do gateway' + (f.taxaEstimada ? ' (estimadas)' : ''), f.taxaGateway == null ? '—' : '- ' + U.money(f.taxaGateway))}
                ${row('Lucro real', semDados ? 'Dados insuficientes' : U.money(f.lucroReal), 'is-total is-profit')}
            </div>
            <div class="gu-section-label">Cobrança</div><div class="gu-detail-list">
                ${row('Frete', o.frete ? U.money(o.frete) : 'Retirada (grátis)')}
                ${o.desconto ? row('Cupom ' + esc(o.cupom || ''), '- ' + U.money(o.desconto)) : ''}
                ${o.juros ? row('Juros repassados à cliente', U.money(o.juros)) : ''}
                ${row('Total', U.money(o.total), 'is-total')}
                ${row('Pagamento', payLabel(o))}
                ${o.entrega.endereco ? row('Entrega', `${esc(o.entrega.endereco.rua)}, ${esc(o.entrega.endereco.numero)} - ${esc(o.entrega.endereco.bairro)}`) : row('Entrega', 'Retirada no local')}
                ${o.clienteTelefone ? row('Telefone', esc(o.clienteTelefone)) : ''}
            </div>`;
        UI.modal({ title: `Pedido #${o.id}`, text: `${o.clienteNome} • ${S.statusLabel(o.status)} • ${U.formatDate(o.criadoEm)}`, html, wide: true, actions: [{ label: 'Fechar', primary: true }] });
    }

    tbody.addEventListener('click', async function (e) {
        if (e.target.closest('[data-retry]')) { load(true); return; }
        const btn = e.target.closest('[data-act]');
        if (!btn) return;
        const id = Number(btn.closest('tr').dataset.id);
        try {
            if (btn.dataset.act === 'details') { const o = all.find(x => x.id === id) || await S.getOrder(id); if (o) details(o); return; }
            const o = await S.getOrder(id, true); // estado atual, direto da API
            if (!o) { UI.toast('Pedido não encontrado.', { type: 'error' }); return; }
            if (btn.dataset.act === 'confirm-address') {
                const a = o.entrega.endereco;
                const ok = await UI.confirm({
                    title: 'Conferir endereço?',
                    message: `O CEP deste pedido não pôde ser validado na hora. Confirme que o bairro e o CEP estão corretos${a ? ` (${a.rua}, ${a.numero} - ${a.bairro}, CEP ${a.cep})` : ''}.`,
                    confirmText: 'Endereço conferido'
                });
                if (!ok) return;
                const res = await S.confirmOrderAddress(id);
                if (!res.ok) { UI.toast(res.message, { type: 'error' }); return; }
                UI.toast(`O endereço do pedido #${id} foi marcado como conferido.`, { type: 'success', title: 'Endereço conferido' });
                await load(true);
                return;
            }
            const next = S.nextStatus(o);
            if (!next) { UI.toast('Este pedido não tem próxima etapa.', { type: 'info' }); await load(true); return; }
            const ok = await UI.confirm({ title: 'Atualizar pedido?', message: `O pedido #${o.id} de ${o.clienteNome} passará de "${S.statusLabel(o.status)}" para "${S.statusLabel(next)}".`, confirmText: 'Atualizar' });
            if (!ok) return;
            const res = await S.advanceOrder(o.id);
            if (!res.ok) { UI.toast(res.message, { type: 'error', title: 'Não foi possível atualizar' }); return; }
            UI.toast(`Pedido #${o.id} agora está "${S.statusLabel(res.status)}".`, { type: 'success', title: 'Status atualizado' });
            await load(true);
        } catch (err) {
            console.error(err);
            UI.toast(err.message || 'Não foi possível concluir a ação.', { type: 'error' });
        }
    });

    L.onSearch = (q) => { query = q; paint(); };
    await load(true);
});
