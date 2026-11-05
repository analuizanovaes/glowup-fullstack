/* Tela: redefinirsenha.html */
GlowUp.ready(function () {
    const { utils: U, store: S, ui: UI, config: C } = GlowUp;
    const R = C.routes;

    const form = U.$('form.form-container');
    const pw = U.$('#new-password');
    const confirm = U.$('#confirm-password');
    const btn = U.$('.btn-submit', form);
    const notice = U.$('.notice-text', form);
    form.noValidate = true;
    UI.autoClear(form);

    UI.bindPasswordRules(pw, U.$('.password-rules'));
    UI.bindPasswordToggle(pw.parentNode.querySelector('svg'), pw);
    UI.bindPasswordToggle(confirm.parentNode.querySelector('svg'), confirm);

    const token = U.query('token');
    const valid = token && S.getRecovery(token);
    if (!valid) {
        notice.textContent = 'Link inválido ou expirado. Solicite um novo link de recuperação.';
        notice.style.color = '#c2185b';
        btn.textContent = 'Solicitar novo link';
        btn.type = 'button';
        btn.addEventListener('click', () => { location.href = R.recover; });
        U.$$('input', form).forEach(i => { i.disabled = true; });
        return;
    }

    form.addEventListener('submit', function (e) {
        e.preventDefault();
        UI.clearErrors(form);
        let ok = true;
        if (!U.isStrongPassword(pw.value)) ok = UI.setError(pw, 'A senha não atende a todos os requisitos.') && ok;
        if (confirm.value !== pw.value || !confirm.value) ok = UI.setError(confirm, 'As senhas não coincidem.') && ok;
        if (!ok) { UI.focusFirstError(form); return; }
        const res = S.resetPassword(token, pw.value);
        if (!res.ok) { UI.toast(res.message, { type: 'error' }); return; }
        UI.flash('Senha redefinida! Entre com a nova senha.', { type: 'success', title: 'Tudo certo' });
        location.href = R.login;
    });
});
