/* Tela: cadastro.html */
GlowUp.ready(function () {
    const { utils: U, store: S, ui: UI, config: C } = GlowUp;
    const R = C.routes;

    const form = U.$('#signup-form') || U.$('form.form-container');
    form.noValidate = true;
    UI.autoClear(form);

    const controls = U.$$('.form-control', form);
    const nome = controls[0], nascimento = controls[1], email = controls[2], telefone = controls[3];
    const senha = U.$('#password-input');
    const terms = U.$('#terms');
    const termsGroup = terms.closest('.checkbox-group');
    nome.autocomplete = 'name'; email.autocomplete = 'email'; telefone.autocomplete = 'tel';
    senha.autocomplete = 'new-password';

    // abas
    const tabs = U.$$('.tab-switch .tab-btn');
    tabs[0].setAttribute('href', R.login);
    tabs[1].setAttribute('href', R.signup);
    U.$('.logo-container').setAttribute('href', R.landing);

    // máscaras e ajudas
    UI.bindDateField(nascimento);
    U.bindMask(telefone, 'phone');
    UI.bindPasswordRules(senha, U.$('.password-rules'));
    UI.bindPasswordToggle(senha.parentNode.querySelector('svg'), senha);

    // telefones extras
    const addPhone = U.$('.btn-add-phone');
    const extraBox = document.createElement('div');
    extraBox.className = 'phone-extras';
    addPhone.parentNode.insertBefore(extraBox, addPhone);
    addPhone.addEventListener('click', function () {
        if (U.$$('.phone-extra', extraBox).length >= 3) { UI.toast('Você pode cadastrar até 4 telefones.', { type: 'info' }); return; }
        const row = document.createElement('div');
        row.className = 'phone-extra';
        row.innerHTML = '<input type="text" class="form-control" placeholder="(61) 99999-9999" inputmode="tel" autocomplete="tel">' +
            '<button type="button" class="phone-extra-remove" aria-label="Remover telefone">&times;</button>';
        extraBox.appendChild(row);
        const input = row.querySelector('input');
        U.bindMask(input, 'phone');
        input.focus();
        row.querySelector('button').addEventListener('click', () => row.remove());
    });

    // Termos de Uso em janela (sem sair da tela)
    const termsLink = termsGroup.querySelector('a');
    termsLink.addEventListener('click', e => { e.preventDefault(); UI.legal('terms'); });
    terms.addEventListener('change', () => {
        const err = termsGroup.nextElementSibling;
        if (err && err.classList.contains('gu-field-error')) err.remove();
    });

    form.addEventListener('submit', function (e) {
        e.preventDefault();
        UI.clearErrors(form);
        const phones = [telefone].concat(U.$$('.phone-extra input', extraBox)).map(i => i.value.trim()).filter(Boolean);
        const data = { nome: nome.value, nascimento: nascimento.value, email: email.value, telefones: phones, senha: senha.value, aceiteTermos: terms.checked };

        // validação visual imediata; o "servidor" (store) repete as regras de negócio
        const res = S.register(data);
        if (!res.ok) {
            const map = { nome, nascimento, email, telefone, senha };
            Object.keys(res.errors).forEach(k => {
                if (k === 'termos') {
                    const err = document.createElement('span');
                    err.className = 'gu-field-error';
                    err.textContent = res.errors[k];
                    termsGroup.insertAdjacentElement('afterend', err);
                } else UI.setError(map[k], res.errors[k]);
            });
            if (res.errors.nascimento && res.errors.nascimento.indexOf('18') >= 0) {
                UI.toast('O cadastro é permitido apenas para maiores de 18 anos.', { type: 'error', title: 'Cadastro bloqueado' });
            }
            UI.focusFirstError(form);
            return;
        }
        UI.flash('Conta criada com sucesso! Entre para conhecer a curadoria.', { type: 'success', title: 'Bem-vinda ao GlowUp' });
        location.href = R.login;
    });
});
