/* Tela: meuperfil.html (perfil da administradora) */
GlowUp.ready(function () {
    const { utils: U, store: S, ui: UI } = GlowUp;
    const me = S.currentUser();
    const form = U.$('.profile-form');
    form.noValidate = true;
    UI.autoClear(form);
    
    // Removido o 'nascimento' daqui
    const [nome, telefone, email] = U.$$('.form-input', form);

    nome.value = me.nome; telefone.value = me.telefones[0] || ''; email.value = me.email;
    U.bindMask(telefone, 'phone');

    form.addEventListener('submit', function (e) {
        e.preventDefault();
        UI.clearErrors(form);
        const phones = [telefone.value].concat(me.telefones.slice(1));
        
        // Removido o 'nascimento' do objeto
        const res = S.updateProfile(me.id, { nome: nome.value, email: email.value, telefones: phones });
        
        if (!res.ok) {
            const map = { nome, email, telefone };
            Object.keys(res.errors).forEach(k => UI.setError(map[k], res.errors[k]));
            UI.focusFirstError(form);
            return;
        }
        UI.toast('Seus dados foram atualizados.', { type: 'success', title: 'Perfil salvo' });
    });
});