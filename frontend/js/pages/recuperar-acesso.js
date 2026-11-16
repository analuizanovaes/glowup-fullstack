/* Tela: recuperaracesso.html */
GlowUp.ready(function () {
    const { utils: U, store: S, ui: UI, config: C } = GlowUp;
    const R = C.routes;

    const form = U.$('form.form-container');
    const email = U.$('input[type=email]', form);
    const btn = U.$('.btn-submit', form);
    form.noValidate = true;
    UI.autoClear(form);

    const tabs = U.$$('.tab-switch .tab-btn');
    tabs[0].setAttribute('href', R.login);
    tabs[1].setAttribute('href', R.signup);
    U.$('.logo-container').setAttribute('href', R.landing);

    form.addEventListener('submit', async function (e) {
        e.preventDefault();
        UI.clearErrors(form);
        if (!U.isEmail(email.value)) { UI.setError(email, 'Informe o e-mail da sua conta.'); email.focus(); return; }
        UI.setLoading(btn, true, 'Enviando...');
        const res = await S.requestPasswordReset(email.value);
        UI.setLoading(btn, false);
        // Mensagem neutra: não revela se o e-mail existe.
        const goReset = { label: 'Digitar o código', onClick: () => { location.href = R.reset + '?email=' + encodeURIComponent(email.value); } };
        UI.toast('Se o e-mail estiver cadastrado, você receberá um código de 6 dígitos em instantes.', {
            type: 'info', title: 'Verifique seu e-mail', duration: 0, actions: [goReset]
        });
    });
});
