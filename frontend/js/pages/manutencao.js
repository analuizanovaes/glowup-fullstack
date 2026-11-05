/* Tela: manutencao.html */
GlowUp.ready(function () {
    const { utils: U, store: S, ui: UI, config: C } = GlowUp;
    const R = C.routes;
    const btn = U.$('.btn-action');
    const admin = S.isAdmin();

    if (admin) {
        btn.textContent = 'Voltar ao painel';
        btn.addEventListener('click', () => { location.href = R.dashboard; });
        return;
    }

    btn.textContent = 'Tentar novamente';
    btn.addEventListener('click', function () {
        if (S.isMaintenance()) {
            UI.toast('Ainda estamos ajustando alguns detalhes. Volte em instantes!', { type: 'info', title: 'Manutenção em andamento' });
        } else {
            location.href = S.currentUser() ? R.store : R.landing;
        }
    });

    // saída para a administradora (ou para trocar de conta)
    const link = document.createElement('a');
    link.className = 'maintenance-admin-link';
    link.href = R.login;
    link.textContent = S.currentUser() ? 'Entrar com outra conta' : 'Sou administradora';
    link.addEventListener('click', function (e) {
        if (!S.currentUser()) return;
        e.preventDefault();
        S.logout();
        location.href = R.login;
    });
    btn.insertAdjacentElement('afterend', link);
});