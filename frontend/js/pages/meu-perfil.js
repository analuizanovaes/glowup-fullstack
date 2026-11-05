/* Tela: meuperfil.html (perfil da administradora) */
GlowUp.ready(function () {
    const { utils: U, store: S, ui: UI } = GlowUp;
    const me = S.currentUser();
    const form = U.$('.profile-form');
    form.noValidate = true;
    UI.autoClear(form);
    const [nome, telefone, email, nascimento] = U.$$('.form-input', form);

    nome.value = me.nome; telefone.value = me.telefones[0] || ''; email.value = me.email; nascimento.value = me.nascimento;
    U.bindMask(telefone, 'phone');
    UI.bindDateField(nascimento);

    form.addEventListener('submit', function (e) {
        e.preventDefault();
        UI.clearErrors(form);
        const phones = [telefone.value].concat(me.telefones.slice(1));
        const res = S.updateProfile(me.id, { nome: nome.value, nascimento: nascimento.value, email: email.value, telefones: phones });
        if (!res.ok) {
            const map = { nome, nascimento, email, telefone };
            Object.keys(res.errors).forEach(k => UI.setError(map[k], res.errors[k]));
            UI.focusFirstError(form);
            return;
        }
        UI.toast('Seus dados foram atualizados.', { type: 'success', title: 'Perfil salvo' });
    });
});
