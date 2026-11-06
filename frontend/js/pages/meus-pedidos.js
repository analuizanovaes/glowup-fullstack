/* Tela: meuspedidos.html */
GlowUp.ready(function () {
    const { utils: U, store: S, ui: UI, layout: L, config: C } = GlowUp;
    const R = C.routes;
    const esc = U.escapeHtml;

    const user = S.currentUser();
    const listEl = U.$('.orders-list');
    const refundIcon = (U.$('.btn-refund svg') || { outerHTML: '' }).outerHTML;
    let query = '';

    // pedido específico pedido pela URL
    const wanted = U.query('pedido');
    if (wanted) {
        const o = S.getOrder(wanted);
        if (!o || o.userId !== user.id) { location.replace(R.notFound); return; }
    }

    const orders = () => S.listOrders({ userId: user.id }).filter(o => {
        if (!query) return true;
        return U.normalize(o.id + ' ' + o.status + ' ' + o.itens.map(i => i.nome).join(' ')).includes(U.normalize(query));
    });

    function card(o) {
        const items = o.itens.map(i => `${esc(i.nome)} • ${i.qtd} un.`).join(' + ');
        const rs = S.returnState(o);
        let action = '';
        if (rs === 'eligible') action = `<button type="button" class="btn-refund" data-act="return">${refundIcon}Solicitar Devolução</button>`;
        else if (rs === 'expired') action = '<span class="order-note">Prazo de devolução encerrado</span>';
        else if (rs === 'rejected') action = '<span class="order-note">Devolução não aprovada (lacre violado)</span>';
        else if (rs === 'requested') action = '<span class="order-note">Devolução em análise</span>';
        else if (rs === 'done') action = '<span class="order-note">Devolução concluída</span>';
        const pay = o.status === 'Aguardando pagamento' && o.pagamento.metodo === 'PIX'
            ? `<a class="order-link" href="${R.pix}?pedido=${o.id}">Pagar agora</a>` : '';
        return `<div class="order-card${o.id === wanted ? ' is-highlight' : ''}" data-id="${o.id}">
            <div class="order-header">
                <div>
                    <div class="order-id">Pedido ${o.id} • ${U.formatDateShort(o.criadoEm)}</div>
                    <div class="order-items">${items}</div>
                </div>
                <span class="status-tag">${esc(o.status)}</span>
            </div>
            <div class="order-footer">
                <span class="order-price">${U.money(o.total)}</span>
                <div class="order-actions">
                    ${pay}
                    <button type="button" class="order-link" data-act="details">Ver detalhes</button>
                    ${action}
                </div>
            </div>
        </div>`;
    }

    function render() {
        const list = orders();
        listEl.innerHTML = list.length ? list.map(card).join('')
            : `<div class="gu-empty">${query ? 'Nenhum pedido encontrado' : 'Você ainda não fez pedidos'}<small>${query ? 'Tente outro termo de busca.' : 'Que tal conhecer a vitrine?'}</small></div>`;
    }

    function details(o) {
        const addr = o.entrega.endereco;
        const row = (a, b, cls) => `<div class="gu-detail-row ${cls || ''}"><span>${a}</span><span>${b}</span></div>`;
        const itens = o.itens.map(i => row(`${esc(i.nome)} × ${i.qtd}`, U.money(i.precoPraticado * i.qtd))).join('');
        const html = `<div class="gu-section-label">Itens</div><div class="gu-detail-list">${itens}</div>
            <div class="gu-section-label">Valores</div><div class="gu-detail-list">
                ${row('Subtotal', U.money(o.subtotal))}
                ${o.desconto ? row(`Cupom ${esc(o.cupom || '')}`, '- ' + U.money(o.desconto)) : ''}
                ${row('Frete', o.frete ? U.money(o.frete) : 'Grátis')}
                ${o.juros ? row('Juros do parcelamento', U.money(o.juros)) : ''}
                ${row('Total', U.money(o.total), 'is-total')}
            </div>
            <div class="gu-section-label">Pagamento e entrega</div><div class="gu-detail-list">
                ${row('Pagamento', o.pagamento.metodo === 'PIX' ? 'Pix' : `Cartão em ${o.pagamento.parcelas}x`)}
                ${row('Recebimento', o.entrega.tipo === 'home' ? `Entrega em casa (até ${o.entrega.prazoDias} dias)` : 'Retirada no local')}
                ${addr ? row('Endereço', `${esc(addr.rua)}, ${esc(addr.numero)} - ${esc(addr.bairro)}`) : ''}
            </div>`;
        UI.modal({ title: `Pedido ${o.id}`, text: `${o.status} • ${U.formatDate(o.criadoEm)}`, html, wide: true, actions: [{ label: 'Fechar', primary: true }] });
    }

    async function requestReturn(o) {
        const node = document.createElement('div');
        node.innerHTML = `
            <div class="gu-field"><label for="ret-motivo">Motivo da devolução</label>
                <select id="ret-motivo" class="gu-select">
                    <option>Arrependimento da compra</option><option>Produto diferente do esperado</option>
                    <option>Não gostei da fragrância</option><option>Outro motivo</option>
                </select></div>
            <label class="gu-check"><input type="checkbox" id="ret-lacre"><span>Declaro que o produto está <strong>lacrado</strong>, sem sinais de uso ou violação do lacre.</span></label>
            <div class="gu-section-label">Como devolver</div>
            <p class="gu-dialog-text">Prazo de ${C.returnWindowDays} dias corridos após a entrega. A entrega do produto é presencial no endereço da revendedora ou, se preferir a retirada, o custo da coleta é da cliente.</p>`;
        const res = await UI.modal({
            title: 'Solicitar devolução', text: `Pedido ${o.id}`, node, icon: UI.icons.box,
            actions: [
                { label: 'Cancelar', value: false },
                { label: 'Enviar solicitação', primary: true, value: true, onClick: (api) => {
                    const chk = node.querySelector('#ret-lacre');
                    if (!chk.checked) { UI.toast('Só aceitamos devolução de produtos lacrados, sem sinais de uso.', { type: 'warning', title: 'Confirme o lacre' }); return false; }
                } }
            ]
        });
        if (res !== true) return;
        const r = S.requestReturn(o.id, { motivo: node.querySelector('#ret-motivo').value, lacrado: true });
        if (!r.ok) { UI.toast(r.message, { type: 'error', title: 'Devolução' }); render(); return; }
        UI.toast('A Domingas vai analisar e retornará em breve.', { type: 'success', title: 'Solicitação enviada' });
        render();
    }

    listEl.addEventListener('click', function (e) {
        const btn = e.target.closest('[data-act]');
        if (!btn) return;
        const o = S.getOrder(btn.closest('.order-card').dataset.id);
        if (btn.dataset.act === 'details') details(o); else requestReturn(o);
    });

    L.onSearch = (q) => { query = q; render(); };
    L.refreshCart();
    render();
    const hl = U.$('.order-card.is-highlight');
    if (hl) hl.scrollIntoView({ block: 'center', behavior: 'smooth' });
});
