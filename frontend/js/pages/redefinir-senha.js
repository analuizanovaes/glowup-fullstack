/* Tela: redefinirsenha.html */
GlowUp.ready(function () {
    const { utils: U, store: S, ui: UI, config: C } = GlowUp;
    const R = C.routes;

    const form = U.$('form.form-container');
    const pw = U.$('#new-password');
    const confirm = U.$('#confirm-password');
    form.noValidate = true;
    UI.autoClear(form);

    UI.bindPasswordRules(pw, U.$('.password-rules'));
    UI.bindPasswordToggle(pw.parentNode.querySelector('svg'), pw);
    UI.bindPasswordToggle(confirm.parentNode.querySelector('svg'), confirm);

    const code = U.$('#otp-code');
    code.addEventListener('input', () => { code.value = U.digits(code.value).slice(0, 6); });

    form.addEventListener('submit', function (e) {
        e.preventDefault();
        UI.clearErrors(form);
        let ok = true;
        if (U.digits(code.value).length !== 6) ok = UI.setError(code, 'Informe o código de 6 dígitos.') && ok;
        if (!U.isStrongPassword(pw.value)) ok = UI.setError(pw, 'A senha não atende a todos os requisitos.') && ok;
        if (confirm.value !== pw.value || !confirm.value) ok = UI.setError(confirm, 'As senhas não coincidem.') && ok;
        if (!ok) { UI.focusFirstError(form); return; }
        const res = S.resetPassword(U.digits(code.value), pw.value);
        if (!res.ok) { UI.setError(code, res.message); code.focus(); return; }
        UI.flash('Senha redefinida! Entre com a nova senha.', { type: 'success', title: 'Tudo certo' });
        location.href = R.login;
    });
});
