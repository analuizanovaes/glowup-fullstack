/* Tela: devolucoes.html. Fluxo do back: SOLICITADA -> análise do lacre (APROVADA/REJEITADA) -> recebimento (RECEBIDA). */
GlowUp.ready(async function () {
    const { utils: U, store: S, ui: UI } = GlowUp;
    const esc = U.escapeHtml;
    const host = U.$('.empty-state-box');
    const holder = document.createElement('div');
    holder.className = 'return-list';
    host.parentNode.insertBefore(holder, host);
    let list = [];
    let seq = 0; // só a resposta da última consulta pinta a tela

    const TAGS = {
        SOLICITADA: ['status-devolucao', 'Em análise'],
        APROVADA: ['status-enviado', 'Aprovada · aguardando recebimento'],
        RECEBIDA: ['status-entregue', 'Concluída'],
        REJEITADA: ['status-cancelado', 'Rejeitada']
    };

    function card(r, order) {
        const tag = TAGS[r.status] || ['status-cancelado', r.status];
        const items = (r.itens || []).map(i => `${esc(i.produtoNome || i.nome)} × ${i.quantidade ?? i.qtd}`).join(' + ');
        const cliente = order?.clienteNome || '';
        const meta = [cliente, `solicitada em ${U.formatDate(r.dataSolicitacao)}`, order ? `pedido ${U.money(order.total)}` : null,
            r.valorEstornado != null && r.status !== 'SOLICITADA' ? `estorno ${U.money(r.valorEstornado)}` : null].filter(Boolean).join(' • ');
        let actions = '';
        if (r.status === 'SOLICITADA' && r.arrependimento) {
            // art. 49: a devolução é sempre aprovada; o lacre só decide se o produto volta à prateleira
            actions = `<button type="button" class="gu-btn gu-btn--primary gu-btn--sm" data-act="approve">Aprovar (lacre intacto)</button>
                <button type="button" class="gu-btn gu-btn--sm" data-act="approve-open">Aprovar (lacre violado)</button>`;
        } else if (r.status === 'SOLICITADA') {
            actions = `<button type="button" class="gu-btn gu-btn--primary gu-btn--sm" data-act="approve">Aprovar (lacre intacto)</button>
                <button type="button" class="gu-btn gu-btn--sm" data-act="reject">Rejeitar (lacre violado)</button>`;
        } else if (r.status === 'APROVADA') {
            actions = '<button type="button" class="gu-btn gu-btn--primary gu-btn--sm" data-act="receive">Confirmar recebimento</button>';
        }
        return `<article class="return-card" data-id="${r.id}">
            <div class="return-main">
                <div class="return-top"><strong>Pedido #${r.pedidoId}</strong><span class="status-badge ${tag[0]}">${tag[1]}</span>${r.arrependimento ? '<span class="status-badge status-devolucao">Arrependimento (art. 49)</span>' : ''}</div>
                <div class="return-meta">${esc(meta)}</div>
                <div class="return-items">${items}</div>
                <div class="return-reason">Motivo: ${esc(r.motivo)}</div>
                ${r.observacaoAnalise ? `<div class="return-reason">Observação da análise: ${esc(r.observacaoAnalise)}</div>` : ''}
            </div>
            ${actions ? `<div class="return-actions">${actions}</div>` : ''}
        </article>`;
    }

    async function load() {
        const mine = ++seq;
        try {
            // pedidos só servem para mostrar cliente e total: se falharem, as devoluções continuam aparecendo
            const [returns, orders] = await Promise.all([S.listReturns(), S.listOrdersAdmin().catch(() => [])]);
            if (mine !== seq) return;
            const byId = new Map(orders.map(o => [o.id, o]));
            const weight = { SOLICITADA: 0, APROVADA: 1 };
            list = returns.slice().sort((a, b) => (weight[a.status] ?? 2) - (weight[b.status] ?? 2) || new Date(b.dataSolicitacao) - new Date(a.dataSolicitacao));
            holder.innerHTML = list.map(r => card(r, byId.get(r.pedidoId))).join('');
            host.style.display = list.length ? 'none' : '';
        } catch (e) {
            console.error(e);
            if (mine !== seq) return;
            list = [];
            holder.innerHTML = UI.errorBox(e.message, 'As devoluções não foram carregadas.');
            host.style.display = 'none';
        }
    }

    holder.addEventListener('click', async function (e) {
        if (e.target.closest('[data-retry]')) { load(); return; }
        const btn = e.target.closest('[data-act]');
        if (!btn) return;
        const id = Number(btn.closest('.return-card').dataset.id);
        const ret = list.find(r => r.id === id);
        if (!ret) return;
        const act = btn.dataset.act;
        let ok, res, toastOk;
        if (act === 'receive') {
            ok = await UI.confirm({ title: 'Confirmar recebimento?', message: `Confirme que a devolução do pedido #${ret.pedidoId} chegou até você. Neste passo o estoque é reposto (se o lacre estava intacto) e o pagamento é estornado.`, confirmText: 'Confirmar recebimento' });
            if (!ok) return;
            res = await S.confirmReturnReceipt(id);
            toastOk = ['Recebimento confirmado. Estoque e estorno atualizados.', 'success'];
        } else {
            const approve = act === 'approve';
            const open = act === 'approve-open';
            ok = await UI.confirm(approve
                ? { title: 'Aprovar devolução?', message: `Confirme que o produto do pedido #${ret.pedidoId} está com o lacre original intacto e sem sinais de uso. As unidades voltam ao estoque quando você confirmar o recebimento.`, confirmText: 'Aprovar' }
                : open
                    ? { title: 'Aprovar com lacre violado?', message: 'Por ser arrependimento (art. 49 do CDC), a devolução e o estorno são garantidos mesmo com o lacre violado. O produto não volta para a prateleira.', confirmText: 'Aprovar' }
                    : { title: 'Rejeitar devolução?', message: 'Com o lacre violado o produto é considerado consumido e não volta para a prateleira.', confirmText: 'Rejeitar', danger: true });
            if (!ok) return;
            res = await S.resolveReturn(id, approve);
            toastOk = approve || open ? ['Devolução aprovada. Confirme o recebimento quando o produto chegar.', 'success'] : ['Devolução rejeitada. O estoque não foi alterado.', 'info'];
        }
        if (!res.ok) { UI.toast(res.message, { type: 'error', title: 'Não foi possível concluir' }); return; }
        UI.toast(toastOk[0], { type: toastOk[1], title: 'Devolução atualizada' });
        await load();
    });

    await load();
});
