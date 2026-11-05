/* Tela: perfil.html (perfil da cliente) */
GlowUp.ready(function () {
    const { utils: U, store: S, ui: UI, layout: L } = GlowUp;
    const esc = U.escapeHtml;
    const user = S.currentUser();

    const greeting = U.$('#greeting-title');
    const nome = U.$('#full-name-input');
    const nascimento = U.$('.date-input-wrapper input');
    const email = U.$('input[type=email]');
    const phoneList = U.$('#phone-list');
    const addPhone = U.$('.btn-add-phone');
    const addrGrid = U.$('.addresses-grid');
    const saveBtn = U.$('.btn-save');

    /* ---- dados pessoais ---- */
    nome.value = user.nome; nascimento.value = user.nascimento; email.value = user.email;
    UI.bindDateField(nascimento);
    UI.autoClear(document.body);
    const updateGreeting = () => { const f = U.firstName(nome.value); greeting.textContent = f ? `Olá, ${f}` : 'Olá!'; };
    nome.addEventListener('input', updateGreeting);
    updateGreeting();

    /* ---- telefones ---- */
    function addPhoneRow(value) {
        phoneList.insertAdjacentHTML('beforeend', UI.phoneRowHtml(value));
        const input = phoneList.lastElementChild.querySelector('input');
        U.bindMask(input, 'phone');
        return input;
    }
    phoneList.innerHTML = '';
    user.telefones.forEach(addPhoneRow);
    addPhone.addEventListener('click', () => {
        if (phoneList.children.length >= 4) { UI.toast('Você pode ter até 4 telefones.', { type: 'info' }); return; }
        addPhoneRow('').focus();
    });
    phoneList.addEventListener('click', function (e) {
        const b = e.target.closest('.btn-icon-trash');
        if (!b) return;
        if (phoneList.children.length <= 1) { UI.toast('Mantenha ao menos um telefone de contato.', { type: 'warning', title: 'Telefone' }); return; }
        b.closest('.phone-item').remove();
    });

    /* ---- endereços ---- */
    const fresh = () => S.getUser(user.id);

    function renderAddresses() {
        const list = fresh().enderecos;
        if (!list.length) { addrGrid.innerHTML = '<div class="gu-empty">Nenhum endereço cadastrado<small>Use "+ Novo endereço" para adicionar.</small></div>'; return; }
        addrGrid.innerHTML = list.map(a => `<div class="address-card${a.principal ? ' selected' : ''}" data-id="${a.id}" tabindex="0">
            <div class="address-title"><div class="radio-dot"></div>${esc(a.apelido)}</div>
            <p class="address-text">${esc(a.rua)}, ${esc(a.numero)}${a.complemento ? ' - ' + esc(a.complemento) : ''}<br>${esc(a.bairro)} • ${esc(a.cidade)}/${esc(a.uf)}<br>CEP ${esc(a.cep)}</p>
            <div class="address-actions">
                <button type="button" class="address-action" data-act="edit">Editar</button>
                <button type="button" class="address-action is-danger" data-act="remove">Remover</button>
            </div>
        </div>`).join('');
    }

    addrGrid.addEventListener('click', async function (e) {
        const card = e.target.closest('.address-card');
        if (!card) return;
        const id = Number(card.dataset.id);
        const act = e.target.closest('[data-act]');
        if (act && act.dataset.act === 'edit') { addressDialog(fresh().enderecos.find(a => a.id === id)); return; }
        if (act && act.dataset.act === 'remove') {
            const ok = await UI.confirm({ title: 'Remover endereço?', message: 'Esse endereço deixará de aparecer no checkout.', confirmText: 'Remover', danger: true });
            if (ok) { S.removeAddress(user.id, id); renderAddresses(); UI.toast('Endereço removido.', { type: 'info' }); }
            return;
        }
        S.setDefaultAddress(user.id, id);
        renderAddresses();
        UI.toast('Esse será o endereço padrão das suas entregas.', { type: 'success', title: 'Endereço padrão' });
    });

    async function addressDialog(existing) {
        const a = existing || { apelido: '', cep: '', rua: '', numero: '', bairro: '', complemento: '', cidade: 'Brasília', uf: 'DF' };
        const node = document.createElement('div');
        node.innerHTML = `
            <div class="gu-grid-2">
                <div class="gu-field"><label for="ad-apelido">Apelido</label><input id="ad-apelido" class="gu-input" placeholder="Casa, Trabalho..." value="${esc(a.apelido)}"></div>
                <div class="gu-field"><label for="ad-cep">CEP</label><input id="ad-cep" class="gu-input" placeholder="00000-000" inputmode="numeric" value="${esc(a.cep)}"></div>
            </div>
            <div class="gu-field"><label for="ad-rua">Rua</label><input id="ad-rua" class="gu-input" value="${esc(a.rua)}"></div>
            <div class="gu-grid-2">
                <div class="gu-field"><label for="ad-numero">Número</label><input id="ad-numero" class="gu-input" value="${esc(a.numero)}"></div>
                <div class="gu-field"><label for="ad-compl">Complemento</label><input id="ad-compl" class="gu-input" placeholder="Opcional" value="${esc(a.complemento || '')}"></div>
            </div>
            <div class="gu-grid-2">
                <div class="gu-field"><label for="ad-bairro">Bairro</label><input id="ad-bairro" class="gu-input" value="${esc(a.bairro)}"></div>
                <div class="gu-field"><label for="ad-cidade">Cidade/UF</label><input id="ad-cidade" class="gu-input" value="${esc(a.cidade)}/${esc(a.uf)}"></div>
            </div>`;
        const $f = (id) => node.querySelector('#' + id);
        U.bindMask($f('ad-cep'), 'cep');
        $f('ad-cep').addEventListener('input', async () => {
            if (U.digits($f('ad-cep').value).length !== 8) return;
            const r = await U.lookupCep($f('ad-cep').value);
            if (r.ok) { $f('ad-rua').value = r.data.rua; $f('ad-bairro').value = r.data.bairro; $f('ad-cidade').value = `${r.data.cidade}/${r.data.uf}`; $f('ad-numero').focus(); }
            else UI.toast('Não encontramos esse CEP agora. Preencha o endereço manualmente.', { type: 'info', title: 'Busca de CEP' });
        });

        await UI.modal({
            title: existing ? 'Editar endereço' : 'Novo endereço', node,
            actions: [
                { label: 'Cancelar', value: false },
                { label: 'Salvar endereço', primary: true, value: true, onClick: () => {
                    let ok = true;
                    node.querySelectorAll('.gu-field-error').forEach(x => x.remove());
                    const need = (id, msg) => { const el = $f(id); el.classList.remove('has-error'); if (!el.value.trim()) { el.classList.add('has-error'); const s = document.createElement('span'); s.className = 'gu-field-error'; s.textContent = msg; el.insertAdjacentElement('afterend', s); ok = false; } };
                    need('ad-apelido', 'Dê um nome ao endereço.'); need('ad-rua', 'Informe a rua.'); need('ad-numero', 'Informe o número.'); need('ad-bairro', 'Informe o bairro.');
                    if (U.digits($f('ad-cep').value).length !== 8) { need('ad-cep', 'CEP inválido.'); $f('ad-cep').classList.add('has-error'); ok = false; }
                    const parts = $f('ad-cidade').value.split('/');
                    if (!parts[0].trim() || (parts[1] || '').trim().length !== 2) { need('ad-cidade', 'Use o formato Cidade/UF.'); ok = false; }
                    if (!ok) return false;
                    const data = { apelido: $f('ad-apelido').value.trim(), cep: $f('ad-cep').value, rua: $f('ad-rua').value.trim(), numero: $f('ad-numero').value.trim(), complemento: $f('ad-compl').value.trim(), bairro: $f('ad-bairro').value.trim(), cidade: parts[0].trim(), uf: parts[1].trim().toUpperCase() };
                    if (existing) data.id = existing.id;
                    S.saveAddress(user.id, data);
                } }
            ]
        }).then(done => { if (done) { renderAddresses(); UI.toast('Endereço salvo.', { type: 'success' }); } });
    }
    U.$('.btn-add-address').addEventListener('click', () => addressDialog(null));

    /* ---- salvar ---- */
    saveBtn.addEventListener('click', function () {
        UI.clearErrors(document.body);
        const phones = U.$$('.phone-input', phoneList);
        const res = S.updateProfile(user.id, { nome: nome.value, nascimento: nascimento.value, email: email.value, telefones: phones.map(p => p.value) });
        if (!res.ok) {
            const map = { nome, nascimento, email };
            Object.keys(res.errors).forEach(k => {
                if (k === 'telefone') { const bad = phones.find(p => !U.isPhone(p.value)) || phones[0]; UI.setError(bad, res.errors[k]); }
                else UI.setError(map[k], res.errors[k]);
            });
            UI.focusFirstError(document.body);
            return;
        }
        UI.toast('Seus dados foram atualizados. Seu histórico de pedidos continua o mesmo.', { type: 'success', title: 'Perfil salvo' });
    });

    L.refreshCart();
    renderAddresses();
});
