/* Tela: login.html */
GlowUp.ready(function () {
    const { utils: U, store: S, ui: UI, config: C } = GlowUp;
    const R = C.routes;

    const form = U.$('.login-form');
    const email = U.$('#email');
    const password = U.$('#password');
    const submit = U.$('#btn-submit-text');
    form.noValidate = true;
    UI.autoClear(form);

    // abas: "Cadastrar" leva para a tela de cadastro
    U.$('#btn-tab-cadastrar').addEventListener('click', () => { location.href = R.signup; });
    U.$('.forgot-link').setAttribute('href', R.recover);
    UI.bindPasswordToggle(U.$('.toggle-password'), password);

    form.addEventListener('submit', async function (e) {
        e.preventDefault();
        UI.clearErrors(form);
        let ok = true;
        if (!U.isEmail(email.value)) ok = UI.setError(email, 'Informe um e-mail válido.') && ok;
        if (!password.value) ok = UI.setError(password, 'Informe sua senha.') && ok;
        if (!ok) { UI.focusFirstError(form); return; }

        UI.setLoading(submit, true, 'Entrando...');
        let wakeProc = null;
        const res = await S.login(email.value, password.value, {
            onWaiting: (attempt) => {
                if (!wakeProc) wakeProc = UI.processing('Acordando o servidor', 'Estamos iniciando nosso servidor seguro para entrar...');
                else wakeProc.setText(`Ainda acordando o servidor... tentativa ${attempt} de 4.`);
            }
        });
        if (wakeProc) wakeProc.close();
            if (!res.ok) {
                UI.setLoading(submit, false);
                UI.setError(password, res.message);
                password.classList.add('gu-shake');
                setTimeout(() => password.classList.remove('gu-shake'), 400);
                UI.toast(res.message, { type: 'error', title: 'Não foi possível entrar' });
                return;
            }
            UI.flash(`Que bom ver você, ${U.firstName(res.user ? res.user.nome : '')}!`, { type: 'success', title: 'Login efetuado' });
            const next = U.query('next');
            const perfil = res.user && (res.user.perfil || res.user.role);
            location.href = perfil === 'ADMIN' ? R.dashboard : (next || R.store);
    });
});
