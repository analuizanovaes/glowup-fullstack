/* Tela: paginaprincipal.html (landing) */
GlowUp.ready(function () {
    const { utils: U, store: S, config: C } = GlowUp;
    const R = C.routes;

    const go = (url) => () => { location.href = url; };
    const primary = U.$('.btn-primary');
    const secondary = U.$('.btn-secondary');

    // a página inicial sempre mostra "Entrar" e "Cadastrar"
    primary.addEventListener('click', go(R.login));
    secondary.addEventListener('click', go(R.signup));

    // janela de Termos de Uso (já existente na página)
    const modal = U.$('#termsModal');
    const open = (e) => { e.preventDefault(); modal.classList.add('active'); };
    const close = () => modal.classList.remove('active');
    const termsLink = U.$$('.footer-links a')[0];
    termsLink.id = 'open-terms';
    termsLink.addEventListener('click', open);
    U.$('.modal-close-btn', modal).addEventListener('click', close);
    modal.addEventListener('click', e => { if (e.target === modal) close(); });
    document.addEventListener('keydown', e => { if (e.key === 'Escape') close(); });
});
