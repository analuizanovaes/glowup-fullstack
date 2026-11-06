/* Tela: devolucoes.html */
GlowUp.ready(function () {
    const { utils: U, store: S, ui: UI } = GlowUp;
    const esc = U.escapeHtml;
    const host = U.$('.empty-state-box');
    const holder = document.createElement('div');
    holder.className = 'return-list';
    host.parentNode.insertBefore(holder, host);

    function card(r) {
        const o = S.getOrder(r.pedidoId);
        const pending = r.status === 'SOLICITADA';
        const items = o.itens.map(i => `${esc(i.nome)} × ${i.qtd}`).join(' + ');
        const tag = pending ? ['status-devolucao', 'Em análise'] : r.status === 'APROVADA' ? ['status-entregue', 'Aprovada'] : ['status-cancelado', 'Rejeitada'];
        return `<article class="return-card" data-id="${r.id}">
            <div class="return-main">
                <div class="return-top"><strong>Pedido ${o.id}</strong><span class="status-badge ${tag[0]}">${tag[1]}</span></div>
                <div class="return-meta">${esc(r.clienteNome)} • solicitada em ${U.formatDate(r.solicitadoEm)} • ${U.money(o.total)}</div>
                <div class="return-items">${items}</div>
                <div class="return-reason">Motivo: ${esc(r.motivo)}</div>
            </div>
            ${pending ? `<div class="return-actions">
                <button type="button" class="gu-btn gu-btn--primary gu-btn--sm" data-act="approve">Aprovar (lacre intacto)</button>
                <button type="button" class="gu-btn gu-btn--sm" data-act="reject">Rejeitar (lacre violado)</button>
            </div>` : ''}
        </article>`;
    }

    function render() {
        const list = S.listReturns();
        holder.innerHTML = list.map(card).join('');
        host.style.display = list.length ? 'none' : '';
    }

    holder.addEventListener('click', async function (e) {
        const btn = e.target.closest('[data-act]');
        if (!btn) return;
        const id = Number(btn.closest('.return-card').dataset.id);
        const approve = btn.dataset.act === 'approve';
        const ret = S.listReturns().find(r => r.id === id);
        const ok = await UI.confirm(approve
            ? { title: 'Aprovar devolução?', message: `Confirme que o produto do pedido ${ret.pedidoId} chegou com o lacre original intacto e sem sinais de uso. As unidades voltam ao estoque.`, confirmText: 'Aprovar e repor estoque' }
            : { title: 'Rejeitar devolução?', message: 'Com o lacre violado o produto é considerado consumido e não volta para a prateleira.', confirmText: 'Rejeitar', danger: true });
        if (!ok) return;
        const res = S.resolveReturn(id, approve);
        if (!res.ok) { UI.toast(res.message, { type: 'error' }); return; }
        UI.toast(approve ? 'Devolução aprovada e estoque atualizado.' : 'Devolução rejeitada. O estoque não foi alterado.', { type: approve ? 'success' : 'info', title: 'Devolução analisada' });
        render();
    });

    render();
});
