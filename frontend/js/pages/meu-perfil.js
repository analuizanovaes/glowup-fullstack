/* Tela: meuperfil.html (perfil da administradora). Dados de GET /api/usuarios/me, gravação em PUT /api/usuarios/me. */
GlowUp.ready(async function () {
    const { utils: U, store: S, ui: UI } = GlowUp;
    const form = U.$('.profile-form');
    form.noValidate = true;
    UI.autoClear(form);

    const [nome, telefone, email] = U.$$('.form-input', form);
    const submit = U.$('button[type=submit]', form);
    U.bindMask(telefone, 'phone');

    function fill() {
        const me = S.currentUser();
        nome.value = me.nome || '';
        telefone.value = (me.telefones || [])[0] || '';
        email.value = me.email || '';
    }
    fill();

    // Trocar o e-mail exige a senha atual (regra do back): pergunta só nesse caso.
    async function askPassword() {
        const node = document.createElement('div');
        node.innerHTML = '<div class="gu-field"><label for="pf-senha-atual">Senha atual</label><input id="pf-senha-atual" type="password" class="gu-input" autocomplete="current-password"></div>';
        const input = node.querySelector('input');
        let typed = '';
        const answer = await UI.modal({
            title: 'Confirme sua senha', text: 'Para trocar o e-mail de acesso, informe a senha atual.', node, icon: UI.icons.question,
            actions: [
                { label: 'Cancelar', value: false },
                { label: 'Confirmar', primary: true, value: true, onClick: () => { typed = input.value; if (!typed) { input.focus(); return false; } } }
            ]
        });
        return answer === true ? typed : null;
    }

    form.addEventListener('submit', async function (e) {
        e.preventDefault();
        UI.clearErrors(form);
        let valid = true;
        if (!nome.value.trim()) valid = UI.setError(nome, 'Informe o nome.') && valid;
        if (!U.isEmail(email.value)) valid = UI.setError(email, 'Informe um e-mail válido.') && valid;
        if (telefone.value.trim() && !U.isPhone(telefone.value)) valid = UI.setError(telefone, 'Informe um telefone com DDD.') && valid;
        if (!valid) { UI.focusFirstError(form); return; }

        const me = S.currentUser();
        let senhaAtual = null;
        if (email.value.trim().toLowerCase() !== String(me.email || '').toLowerCase()) {
            senhaAtual = await askPassword();
            if (senhaAtual === null) return;
        }

        UI.setLoading(submit, true, 'Salvando...');
        const phones = [telefone.value].concat((me.telefones || []).slice(1));
        const res = await S.updateProfile(me.id, { nome: nome.value.trim(), email: email.value.trim(), senhaAtual, telefones: phones });
        UI.setLoading(submit, false);

        if (!res.ok) {
            const msg = res.message || 'Não foi possível salvar o perfil.';
            const target = /senha/i.test(msg) ? null : /e-?mail/i.test(msg) ? email : /telefone/i.test(msg) ? telefone : /nome/i.test(msg) ? nome : null;
            if (target) { UI.setError(target, msg); UI.focusFirstError(form); } else UI.toast(msg, { type: 'error', title: 'Perfil não salvo' });
            return;
        }
        fill();
        UI.toast('Seus dados foram atualizados.', { type: 'success', title: 'Perfil salvo' });
    });
});
