/* Tela: crm.html (CRM de reposição) */
GlowUp.ready(function () {
    const { utils: U, store: S, ui: UI, layout: L } = GlowUp;
    const esc = U.escapeHtml;
    const grid = U.$('.crm-grid');
    let query = U.query('q') || '';

    const when = (d) => d < 0 ? `há ${-d} dia${d === -1 ? '' : 's'}` : d === 0 ? 'hoje' : `em ${d} dia${d === 1 ? '' : 's'}`;

    function render() {
        const n = U.normalize(query);
        const list = S.crmSuggestions().filter(s => !n || U.normalize(s.user.nome + ' ' + s.produto).includes(n));
        grid.innerHTML = list.length ? list.map((s, i) => `
            <div class="crm-card" data-i="${i}">
                <div class="crm-card-header">
                    <span class="crm-badge">${s.dias < 0 ? 'Reposição atrasada' : 'Contato sugerido'}</span>
                    <span class="crm-days">${when(s.dias)}</span>
                </div>
                <div class="crm-client-name">${esc(s.user.nome)}</div>
                <div class="crm-desc">Comprou ${esc(s.produto)}. Uso médio estimado: ${s.usoMedioDias} dias.</div>
                <button type="button" class="btn-primary" style="align-self: flex-start; margin-top: 0.5rem;">Preparar mensagem</button>
            </div>`).join('')
            : '<div class="gu-empty">Nenhuma reposição prevista para os próximos dias<small>Quando o tempo médio de uso de um produto estiver acabando, a cliente aparece aqui.</small></div>';
        grid._list = list;
    }

    grid.addEventListener('click', function (e) {
        const btn = e.target.closest('.btn-primary');
        if (!btn) return;
        const s = grid._list[Number(btn.closest('.crm-card').dataset.i)];
        const me = S.currentUser();
        const text = `Oi, ${U.firstName(s.user.nome)}! Aqui é a ${U.firstName(me.nome)}, da GlowUp. Já faz um tempinho que você levou ${s.produto}, e ele deve estar acabando. Quer que eu separe um novo pra você? Posso te mostrar as novidades também.`;
        const node = document.createElement('div');
        node.innerHTML = `<div class="gu-field"><label for="crm-msg">Mensagem para ${esc(U.firstName(s.user.nome))}</label><textarea id="crm-msg" class="gu-textarea" rows="6">${esc(text)}</textarea></div>`;
        const area = node.querySelector('textarea');
        const phone = U.digits((s.user.telefones || [])[0] || '');
        UI.modal({
            title: 'Preparar mensagem', text: `${s.user.nome} • reposição ${when(s.dias)}`, node, icon: UI.icons.heart,
            actions: [
                { label: 'Fechar', value: false },
                { label: 'Copiar mensagem', value: 'copy', onClick: async () => {
                    const ok = await U.copyText(area.value);
                    UI.toast(ok ? 'Mensagem copiada.' : 'Selecione o texto e copie manualmente.', { type: ok ? 'success' : 'warning' });
                    return false;
                } },
                { label: 'Abrir WhatsApp', primary: true, value: 'wa', onClick: () => {
                    if (!phone) { UI.toast('Esta cliente não tem telefone cadastrado.', { type: 'warning' }); return false; }
                    window.open(`https://wa.me/55${phone}?text=${encodeURIComponent(area.value)}`, '_blank', 'noopener');
                } }
            ]
        });
    });

    L.onSearch = (q) => { query = q; render(); };
    render();
});
