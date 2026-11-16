/* Tela: crm.html (CRM de reposição). Sugestões de GET /api/admin/reposicoes. */
GlowUp.ready(async function () {
    const { utils: U, store: S, ui: UI, layout: L } = GlowUp;
    const esc = U.escapeHtml;
    const grid = U.$('.crm-grid');
    let query = U.query('q') || '';
    let all = null;
    let shown = [];
    let seq = 0;

    const when = (d) => d == null ? 'sem data prevista' : d < 0 ? `há ${-d} dia${d === -1 ? '' : 's'}` : d === 0 ? 'hoje' : `em ${d} dia${d === 1 ? '' : 's'}`;

    function paint() {
        const n = U.normalize(query);
        shown = all.filter(s => !n || U.normalize(s.user.nome + ' ' + s.produto).includes(n))
            .sort((a, b) => (a.dias ?? 9999) - (b.dias ?? 9999));
        grid.innerHTML = shown.length ? shown.map((s, i) => `
            <div class="crm-card" data-i="${i}">
                <div class="crm-card-header">
                    <span class="crm-badge">${s.dias != null && s.dias < 0 ? 'Reposição atrasada' : 'Contato sugerido'}</span>
                    <span class="crm-days">${when(s.dias)}</span>
                </div>
                <div class="crm-client-name">${esc(s.user.nome)}</div>
                <div class="crm-desc">Comprou ${esc(s.produto)}. Uso médio estimado: ${s.usoMedioDias ?? '—'} dias.</div>
                <div style="display:flex; gap:.5rem; flex-wrap:wrap; margin-top:.5rem">
                    <button type="button" class="btn-primary" data-act="prepare" style="align-self: flex-start;">Preparar mensagem</button>
                    <button type="button" class="btn-secondary" data-act="discard" style="align-self: flex-start;">Descartar</button>
                </div>
            </div>`).join('')
            : `<div class="gu-empty">${all.length ? 'Nenhuma sugestão encontrada' : 'Nenhuma reposição prevista'}<small>${all.length ? 'Tente outro termo de busca.' : 'Quando o tempo médio de uso de um produto estiver acabando, a cliente aparece aqui.'}</small></div>`;
    }

    async function load() {
        const mine = ++seq;
        try {
            const list = await S.crmSuggestions();
            if (mine !== seq) return;
            all = list;
            paint();
        } catch (e) {
            console.error(e);
            if (mine !== seq) return;
            all = null; shown = [];
            grid.innerHTML = UI.errorBox(e.message, 'As sugestões de reposição não foram carregadas.');
        }
    }

    async function setStatus(s, status, okMsg) {
        const res = await S.updateRestock(s.id, status);
        if (!res.ok) { UI.toast(res.message || 'Não foi possível atualizar a sugestão.', { type: 'error' }); return false; }
        UI.toast(okMsg, { type: 'success', title: 'CRM atualizado' });
        await load();
        return true;
    }

    grid.addEventListener('click', async function (e) {
        if (e.target.closest('[data-retry]')) { load(); return; }
        const btn = e.target.closest('[data-act]');
        if (!btn) return;
        const s = shown[Number(btn.closest('.crm-card').dataset.i)];
        if (!s) return;

        if (btn.dataset.act === 'discard') {
            const ok = await UI.confirm({ title: 'Descartar sugestão?', message: `A sugestão de reposição para ${s.user.nome} sai da lista.`, confirmText: 'Descartar', danger: true });
            if (ok) await setStatus(s, 'DESCARTADA', 'Sugestão descartada.');
            return;
        }

        const me = S.currentUser();
        const text = `Oi, ${U.firstName(s.user.nome)}! Aqui é a ${U.firstName(me.nome)}, da GlowUp. Já faz um tempinho que você levou ${s.produto}, e ele deve estar acabando. Quer que eu separe um novo pra você? Posso te mostrar as novidades também.`;
        const node = document.createElement('div');
        node.innerHTML = `<div class="gu-field"><label for="crm-msg">Mensagem para ${esc(U.firstName(s.user.nome))}</label><textarea id="crm-msg" class="gu-textarea" rows="6">${esc(text)}</textarea></div>`;
        const area = node.querySelector('textarea');
        const phone = U.digits((s.user.telefones || [])[0] || '');
        const answer = await UI.modal({
            title: 'Preparar mensagem', text: `${s.user.nome} • reposição ${when(s.dias)}`, node, icon: UI.icons.heart,
            actions: [
                { label: 'Fechar', value: false },
                { label: 'Copiar mensagem', value: 'copy', onClick: async () => {
                    const ok = await U.copyText(area.value);
                    UI.toast(ok ? 'Mensagem copiada.' : 'Selecione o texto e copie manualmente.', { type: ok ? 'success' : 'warning' });
                    return false;
                } },
                { label: 'Marcar como contatada', value: 'done' },
                { label: 'Abrir WhatsApp', primary: true, value: 'wa', onClick: () => {
                    if (!phone) { UI.toast('Esta cliente não tem telefone cadastrado.', { type: 'warning' }); return false; }
                    window.open(`https://wa.me/55${phone}?text=${encodeURIComponent(area.value)}`, '_blank', 'noopener');
                } }
            ]
        });
        if (answer === 'wa' || answer === 'done') await setStatus(s, 'CONTATADA', 'Sugestão marcada como contatada.');
    });

    L.onSearch = (q) => { query = q; if (all) paint(); };
    await load();
});
